package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.JobClass;
import org.ihtsdo.termserver.scripting.client.ReportingServerClient;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ExecutionOptions;
import org.ihtsdo.termserver.scripting.util.UserInteractionHelper;
import org.ihtsdo.termserver.scripting.util.WhitelistHelper;
import org.snomed.otf.scheduler.domain.Job;
import org.snomed.otf.scheduler.domain.WhiteListedConcept;
import org.springframework.web.client.RestClientException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * XDS-450 Add the concepts listed in a file (-f) to a report's whitelist via the reporting service API,
 * keeping whatever is already whitelisted.  One SCTID per line, optionally followed by the FSN (tab separated
 * or as |FSN|).  Blank lines and # comments are ignored.  Where no FSN is given, it's taken from the snapshot.
 * All questions are asked before the snapshot loads.
 */
public class WhitelistReportConcept extends TermServerReport implements JobClass {

	private static final int SUMMARY = PRIMARY_REPORT;
	private static final int PROCESSING = SECONDARY_REPORT;
	private static final String RUN_DETAILS = "Run details";
	private static final String QUESTION = "Question";
	private static final String WHITELIST = "Whitelist";
	private static final String WHITELIST_FOR = " whitelist for ";
	private static final String IN_FILE = "Concepts in file";
	private static final String ALREADY_WHITELISTED = "Already whitelisted";
	private static final String ADDED = "Added";
	private static final String NOT_IN_SNAPSHOT = "Not found in snapshot";
	private static final String RELEASE_ISSUES_REPORT = "Release Issues Report";
	private static final String SCTID_PATTERN = "\\d{6,18}";

	private final UserInteractionHelper ui = new UserInteractionHelper(this);

	private String reportName;
	private String codeSystem;
	private ReportingServerClient client;
	private Map<String, String> requested;
	private Map<String, WhiteListedConcept> whiteListBySctId;
	private int existingCount;
	private boolean proceed;
	//Recorded as asked, then written to the Summary tab once it exists
	private final List<String[]> runDetails = new ArrayList<>();

	public static void main(String[] args) throws TermServerScriptException {
		new WhitelistReportConcept().standardExecution(args, ExecutionOptions.DEFAULT);
	}

	@Override
	public Job getJob() {
		return null;
	}

	@Override
	protected void init(String[] args) throws TermServerScriptException {
		super.init(args);
		File inputFile = getInputFileOrThrow(0);
		runDetails.add(new String[] {RUN_DETAILS, "Input file", inputFile.getAbsolutePath()});
		runDetails.add(new String[] {RUN_DETAILS, "Project", project.getKey() + " on " + project.getBranchPath()});
		requested = readConceptsFromFile(inputFile);
		reportName = ask("Report to whitelist for", RELEASE_ISSUES_REPORT);
		codeSystem = ask("Code system", WhitelistHelper.deriveCodeSystem(project.getBranchPath()));

		client = new ReportingServerClient(url, getAuthenticatedCookie());
		whiteListBySctId = fetchWhiteList();
		existingCount = whiteListBySctId.size();
		long newCount = requested.keySet().stream().filter(sctId -> !whiteListBySctId.containsKey(sctId)).count();
		print("Currently " + existingCount + " concepts whitelisted for " + reportName + " in " + codeSystem
				+ ", of which " + (requested.size() - newCount) + " are in the file\n");

		//Ask now, so nobody has to wait for the snapshot to load before answering
		if (newCount > 0) {
			String question = "Add " + newCount + " concepts to the " + reportName + WHITELIST_FOR + codeSystem;
			proceed = ui.askYesNo(question, true);
			runDetails.add(new String[] {QUESTION, question + "?", proceed ? "Y" : "N"});
		}
	}

	private String ask(String label, String defaultValue) {
		String answer = ui.askWithDefault(label, defaultValue);
		runDetails.add(new String[] {QUESTION, label + "?", answer});
		return answer;
	}

	@Override
	public void postInit() throws TermServerScriptException {
		postInit(new String[] {"Summary", "Processing"},
				new String[] {"Category, Item, Value, , , ", "SCTID, FSN, Action, , "});
		summaryTabIdx = SUMMARY;
		for (String[] detail : runDetails) {
			report(SUMMARY, (Object[]) detail);
		}
		initialiseSummaryCount(WHITELIST, IN_FILE);
		initialiseSummaryCount(WHITELIST, ALREADY_WHITELISTED);
		initialiseSummaryCount(WHITELIST, ADDED);
		initialiseSummaryCount(WHITELIST, NOT_IN_SNAPSHOT);
	}

	@Override
	public void runJob() throws TermServerScriptException {
		try {
			addToWhiteList();
		} finally {
			reportSummaryCounts(SUMMARY);
		}
	}

	private void addToWhiteList() throws TermServerScriptException {
		incrementSummaryCount(WHITELIST, IN_FILE, requested.size());
		incrementSummaryCount(WHITELIST, "Whitelist size before", existingCount);
		List<String> toAdd = new ArrayList<>();
		for (Map.Entry<String, String> entry : requested.entrySet()) {
			String sctId = entry.getKey();
			if (whiteListBySctId.containsKey(sctId)) {
				report(PROCESSING, sctId, whiteListBySctId.get(sctId).getFsn(), ALREADY_WHITELISTED);
				incrementSummaryCount(WHITELIST, ALREADY_WHITELISTED);
			} else {
				whiteListBySctId.put(sctId, createWhiteListedConcept(sctId, entry.getValue()));
				toAdd.add(sctId);
			}
		}

		if (toAdd.isEmpty()) {
			report(PROCESSING, "", "", "Nothing to add");
			return;
		}

		if (!proceed) {
			report(PROCESSING, "", "", "Cancelled - nothing added");
			return;
		}

		try {
			client.setWhiteListForReport(reportName, codeSystem, new HashSet<>(whiteListBySctId.values()));
		} catch (RestClientException e) {
			throw new TermServerScriptException("Failed to update the " + reportName + WHITELIST_FOR + codeSystem, e);
		}
		confirmAdded(toAdd);
		incrementSummaryCount(WHITELIST, "Whitelist size after", whiteListBySctId.size());
	}

	private Map<String, String> readConceptsFromFile(File file) throws TermServerScriptException {
		Map<String, String> concepts = new LinkedHashMap<>();
		List<String> lines;
		try {
			lines = Files.readAllLines(file.toPath());
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to read " + file, e);
		}

		int lineNum = 0;
		for (String line : lines) {
			lineNum++;
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}
			String[] parts = trimmed.split("[\\t|]", 2);
			String sctId = parts[0].trim();
			if (!sctId.matches(SCTID_PATTERN)) {
				throw new TermServerScriptException("Line " + lineNum + " of " + file + " does not start with an SCTID: " + line);
			}
			String fsn = parts.length > 1 ? parts[1].replace("|", "").trim() : "";
			concepts.putIfAbsent(sctId, fsn);
		}
		print("Read " + concepts.size() + " distinct concepts from " + file + "\n");
		return concepts;
	}

	private Map<String, WhiteListedConcept> fetchWhiteList() throws TermServerScriptException {
		Map<String, WhiteListedConcept> current = new LinkedHashMap<>();
		try {
			client.getWhiteListForReport(reportName, codeSystem)
					.forEach(w -> current.put(w.getSctId(), w));
		} catch (RestClientException e) {
			throw new TermServerScriptException("Failed to recover the " + reportName + WHITELIST_FOR + codeSystem, e);
		}
		return current;
	}

	private WhiteListedConcept createWhiteListedConcept(String sctId, String fsn) throws TermServerScriptException {
		if (fsn.isEmpty()) {
			Concept c = gl.getConcept(sctId, false, false);
			if (c == null) {
				report(PROCESSING, sctId, "", NOT_IN_SNAPSHOT + " - whitelisting without FSN");
				incrementSummaryCount(WHITELIST, NOT_IN_SNAPSHOT);
			} else {
				fsn = c.getFsn();
			}
		}
		WhiteListedConcept w = new WhiteListedConcept();
		w.setSctId(sctId);
		w.setFsn(fsn);
		return w;
	}

	private void confirmAdded(List<String> toAdd) throws TermServerScriptException {
		Set<String> nowWhiteListed = fetchWhiteList().keySet();
		for (String sctId : toAdd) {
			boolean added = nowWhiteListed.contains(sctId);
			report(PROCESSING, sctId, whiteListBySctId.get(sctId).getFsn(), added ? ADDED : "FAILED - not found in whitelist after update");
			incrementSummaryCount(WHITELIST, added ? ADDED : "Failed to add");
		}
		if (!nowWhiteListed.containsAll(toAdd)) {
			throw new TermServerScriptException("Whitelist update for " + reportName + " in " + codeSystem + " did not take effect for all concepts - see report");
		}
	}
}
