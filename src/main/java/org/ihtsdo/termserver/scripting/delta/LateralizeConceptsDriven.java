package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.template.NormaliseConcepts;
import org.ihtsdo.termserver.scripting.util.ConceptLateralizer;
import org.ihtsdo.termserver.scripting.util.TermGenerationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

public class LateralizeConceptsDriven extends DeltaGeneratorWithMultiAutoImport implements ScriptConstants, TermGenerationStrategy {

	Set<String> whitelist = new HashSet<>();
	private static final Logger LOGGER = LoggerFactory.getLogger(LateralizeConceptsDriven.class);
 	private ConceptLateralizer conceptLateralizer;
	private NormaliseConcepts conceptNormalizer = null;
	private Map<Concept, LateralizeInstruction> lateralizedInstructionMap = new LinkedHashMap<>();
	private final Map<Concept, LateralizeInstruction> allInstructionsMap = new HashMap<>();
	//The source concept currently being lateralized, so that reports about its clones can carry its comment
	private Concept currentSourceConcept = null;

	//Input file columns: 0 = conceptId, 2 = reason for skipping (if populated), 3 = override PT, 4 = author comment
	private static final int SKIP_REASON_COL = 2;
	private static final int OVERRIDE_PT_COL = 3;
	private static final int COMMENT_COL = 4;

	private static final int SKIPPED_REPORT = TERTIARY_REPORT;

	private static final int SOURCE_CONCEPTS_PER_ARCHIVE = 20; //This will give us 60 concepts in the output
	
	public static void main(String[] args) throws TermServerScriptException {
		new LateralizeConceptsDriven().standardExecutionWithIds(args);
	}

	@Override
	public void postInit(String googleFolder) throws TermServerScriptException {
		additionalReportColumns += ", , ";
		String[] columnHeadings = new String[]{
				"SCTID, FSN, SemTag, EWO Comment, Severity, Action, Details," + additionalReportColumns,
				"SCTID, FSN, SemTag, EWO Comment, Example Usage ,",
				"SCTID, FSN, SemTag, EWO Comment, Skip Reason"
		};

		String[] tabNames = new String[]{
				"Processing Report",
				"Unlateralized BodyStructs",
				"Skipped"
		};
		super.postInit(googleFolder, tabNames, columnHeadings);
		conceptLateralizer = ConceptLateralizer.get(this, true, this);
		conceptLateralizer.addPluralityException("proper");
		conceptLateralizer.addPluralityException("type II");
		conceptLateralizer.addPluralityException("region");
		conceptLateralizer.addPluralityException("thinning");

		//NormaliseConcepts reports in its own task-based format, so route its lines through our columns instead
		conceptNormalizer = new NormaliseConcepts(this) {
			@Override
			public void report(Task task, Component component, Severity severity, ReportActionType actionType, Object... details) throws TermServerScriptException {
				if (component instanceof Concept concept) {
					LateralizeConceptsDriven.this.report(concept, severity, actionType, details);
				} else {
					super.report(task, component, severity, actionType, details);
				}
			}
		};
	}

	@Override
	protected void process() throws TermServerScriptException {
		populateWhitelist();
		populateLateralizedInstructionMap();
		List<Component> conceptsToLateralize = new ArrayList<>(lateralizedInstructionMap.keySet());
		int conceptsProcessedInThisBatch = 0;
		for (LateralizeInstruction li : lateralizedInstructionMap.values()) {
			currentSourceConcept = li.concept;
			conceptNormalizer.normaliseConcept(null, li.concept, null);
			report(li.concept, Severity.NONE, ReportActionType.INFO, li.concept, li.concept.toExpression(CharacteristicType.STATED_RELATIONSHIP));
			conceptLateralizer.createLateralizedConceptIfRequired(li.concept, LEFT, conceptsToLateralize);
			conceptLateralizer.createLateralizedConceptIfRequired(li.concept, RIGHT, conceptsToLateralize);
			conceptLateralizer.createLateralizedConceptIfRequired(li.concept, BILATERAL, conceptsToLateralize);
			conceptsProcessedInThisBatch++;
			currentSourceConcept = null;

			if (conceptsProcessedInThisBatch >= SOURCE_CONCEPTS_PER_ARCHIVE) {
				//ConceptLateralizer records each concept it writes via recordConceptWritten(), so
				//conceptsInLastBatch is already the true count - createOutputArchive's own isModified()
				//scan will find nothing further, since ConceptLateralizer cleans concepts as it goes.
				createOutputArchive(true, conceptsInLastBatch);
				initialiseOutputDirectory();
				initialiseFileHeaders();
				conceptsProcessedInThisBatch = 0;
				resetConceptsWrittenCount();
			}
		}
	}

	private void populateWhitelist() throws TermServerScriptException {
		try {
			for (String line : Files.readAllLines(getInputFile(2).toPath(), Charset.defaultCharset())) {
				whitelist.add(line.trim());
			}
			LOGGER.info("Populated whitelist with {} concepts", whitelist.size());
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}

	private void populateLateralizedInstructionMap() throws TermServerScriptException {
		List<String> lines;
		try {
			lines = Files.readAllLines(getInputFile().toPath(), Charset.defaultCharset());
		} catch (IOException e) {
			throw new TermServerScriptException(e);
		}
		for (String line : lines) {
			parseLateralizedInstructionMapLine(line);
		}
		LOGGER.info("Populated instruction map with {} concepts to lateralize, {} skipped",
				lateralizedInstructionMap.size(), allInstructionsMap.size() - lateralizedInstructionMap.size());
	}

	private void parseLateralizedInstructionMapLine(String line) throws TermServerScriptException {
		if (line.isBlank()) {
			return;
		}
		LateralizeInstruction li;
		try {
			li = parseLateralityInstruction(gl, line.split(TAB));
		} catch (Exception e) {
			LOGGER.warn("Failed to parse line: {}", line);
			return;
		}
		allInstructionsMap.put(li.concept, li);
		String skipReason = determineSkipReason(li);
		if (skipReason == null) {
			lateralizedInstructionMap.put(li.concept, li);
		} else {
			report(SKIPPED_REPORT, li.concept, skipReason);
		}
	}

	private String determineSkipReason(LateralizeInstruction li) {
		//Any reason given in the input file is a reason to skip
		if (li.skipReason != null) {
			return li.skipReason;
		}
		//Is this one of the concepts we've been told is safe to lateralize?
		if (!whitelist.contains(li.concept.getId())) {
			return "Not in whitelist";
		}
		if (!li.concept.isActiveSafely()) {
			return "Concept is inactive";
		}
		return null;
	}

	@Override
	public boolean report(int reportIdx, Concept c, Object... details) throws TermServerScriptException {
		//Every tab has the author's comment (if any) for the concept straight after the SemTag
		Object[] detailsWithComment = new Object[details.length + 1];
		detailsWithComment[0] = getComment(c);
		System.arraycopy(details, 0, detailsWithComment, 1, details.length);
		return super.report(reportIdx, c, detailsWithComment);
	}

	private String getComment(Concept c) {
		if (c == null) {
			return "";
		}
		//Reports about lateralized clones or body structures carry the comment of the source concept being processed
		LateralizeInstruction li = allInstructionsMap.get(c);
		if (li == null && currentSourceConcept != null) {
			li = allInstructionsMap.get(currentSourceConcept);
		}
		return li == null || li.comment == null ? "" : li.comment;
	}

	@Override
	public boolean applyTermViaOverride(Concept original, Concept clone, String lateralityStr) throws TermServerScriptException {
		//Do we have an override for this concept?
		LateralizeInstruction li = lateralizedInstructionMap.get(original);
		if (li != null && li.pt != null) {
			String pt = li.pt;
			//The override is usually given as an example.  Modify for this specific laterality
			if (lateralityStr.equals("right")) {
				pt = replaceLeft(pt, "right");
			} else if (lateralityStr.contains("bilateral")) {
				pt = replaceLeft(pt, "bilateral");
			}
			conceptLateralizer.applyTermAsPtAndFsn(clone, pt);
			return true;
		}
		return false;
	}

	private String replaceLeft(String term, String lateralityStr) {
		//Preserve case, eg 'Left eye' -> 'Right eye', 'of left eye' -> 'of right eye'
		String capitalized = Character.toUpperCase(lateralityStr.charAt(0)) + lateralityStr.substring(1);
		return term.replaceAll("\\bleft\\b", lateralityStr)
				.replaceAll("\\bLeft\\b", capitalized);
	}

	@Override
	public String suggestTerm(Concept concept, String termModifier) {
		String suffix = termModifier.contains("bilateral")? " eyes" : " eye";
		return concept.getPreferredSynonym() + " of " + termModifier + suffix;
	}

	class LateralizeInstruction {
		Concept concept;
		String pt;
		String skipReason;
		String comment;

		public LateralizeInstruction(Concept concept, String pt, String skipReason, String comment) {
			this.concept = concept;
			this.pt = pt;
			this.skipReason = skipReason;
			this.comment = comment;
		}
	}

	public LateralizeInstruction parseLateralityInstruction(GraphLoader gl, String[] items) throws TermServerScriptException {
		String conceptId = items[0].trim();
		Concept concept = gl.getConcept(conceptId);
		if (concept == null) {
			throw new TermServerScriptException("Concept not found: " + conceptId);
		}
		return new LateralizeInstruction(concept,
				getOptionalItem(items, OVERRIDE_PT_COL),
				getOptionalItem(items, SKIP_REASON_COL),
				getOptionalItem(items, COMMENT_COL));
	}

	private String getOptionalItem(String[] items, int idx) {
		if (items.length > idx && !items[idx].isBlank()) {
			return items[idx].trim();
		}
		return null;
	}

}
