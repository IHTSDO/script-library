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

	private static final int PROCESSING = PRIMARY_REPORT;
	private static final String RELEASE_ISSUES_REPORT = "Release Issues Report";
	private static final String SCTID_PATTERN = "\\d{6,18}";

	private final UserInteractionHelper ui = new UserInteractionHelper(this);

	private String reportName;
	private String codeSystem;
	private ReportingServerClient client;
	private Map<String, String> requested;
	private Map<String, WhiteListedConcept> whiteList;
	private int existingCount;
	private boolean proceed;

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
		requested = readConceptsFromFile(getInputFileOrThrow(0));
		reportName = ui.askWithDefault("Report to whitelist for", RELEASE_ISSUES_REPORT);
		codeSystem = ui.askWithDefault("Code system", WhitelistHelper.deriveCodeSystem(project.getBranchPath()));

		client = new ReportingServerClient(url, getAuthenticatedCookie());
		whiteList = fetchWhiteList();
		existingCount = whiteList.size();
		long newCount = requested.keySet().stream().filter(sctId -> !whiteList.containsKey(sctId)).count();
		print("Currently " + existingCount + " concepts whitelisted for " + reportName + " in " + codeSystem
				+ ", of which " + (requested.size() - newCount) + " are in the file\n");

		//Ask now, so nobody has to wait for the snapshot to load before answering
		proceed = newCount > 0
				&& ui.askYesNo("Add " + newCount + " concepts to the " + reportName + " whitelist for " + codeSystem, true);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		postInit(new String[] {"Processing"}, new String[] {"SCTID, FSN, Action, , "});
	}

	@Override
	public void runJob() throws TermServerScriptException {
		List<String> toAdd = new ArrayList<>();
		for (Map.Entry<String, String> entry : requested.entrySet()) {
			String sctId = entry.getKey();
			if (whiteList.containsKey(sctId)) {
				report(PROCESSING, sctId, whiteList.get(sctId).getFsn(), "Already whitelisted");
			} else {
				whiteList.put(sctId, createWhiteListedConcept(sctId, entry.getValue()));
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
			client.setWhiteListForReport(reportName, codeSystem, new HashSet<>(whiteList.values()));
		} catch (RestClientException e) {
			throw new TermServerScriptException("Failed to update the " + reportName + " whitelist for " + codeSystem, e);
		}
		confirmAdded(toAdd);
		report(PROCESSING, "", "", "Whitelist now holds " + whiteList.size() + " concepts, was " + existingCount);
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
			throw new TermServerScriptException("Failed to recover the " + reportName + " whitelist for " + codeSystem, e);
		}
		return current;
	}

	private WhiteListedConcept createWhiteListedConcept(String sctId, String fsn) throws TermServerScriptException {
		if (fsn.isEmpty()) {
			Concept c = gl.getConcept(sctId, false, false);
			if (c == null) {
				report(PROCESSING, sctId, "", "Not found in snapshot - whitelisting without FSN");
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
			String action = nowWhiteListed.contains(sctId) ? "Added" : "FAILED - not found in whitelist after update";
			report(PROCESSING, sctId, whiteList.get(sctId).getFsn(), action);
		}
		if (!nowWhiteListed.containsAll(toAdd)) {
			throw new TermServerScriptException("Whitelist update for " + reportName + " in " + codeSystem + " did not take effect for all concepts - see report");
		}
	}
}
