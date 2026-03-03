package org.ihtsdo.termserver.scripting.fixes.one_offs;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;


/**
 * RP-605 List any concepts (which will almost certainly be inactive) where the FSN
 * does not contain a currently valid semantic tag.
 */
public class ReplaceInvalidSemanticTags extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(ReplaceInvalidSemanticTags.class);

	private List<String> historicallyAcceptableSemTags = List.of("(administrative concept)",
			"(context-dependent category)",
			"(environment / location)",
			"(special concept)");

	private Map<String, String> knownReplacements = Map.of(
		"(virtual clinical drug)", "(clinical drug)",
		"(biological function)","(substance)",
		"(inactive concept)", "(foundation metadata concept)"
	);

	private Set<String> validSemTags = new HashSet<>();

	protected ReplaceInvalidSemanticTags(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException {
		ReplaceInvalidSemanticTags fix = new ReplaceInvalidSemanticTags(null);
		try {
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.init(args);
			fix.loadProjectSnapshot(true);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"); //Ad-hoc
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		//Work through all top level hierarchies and list semantic tags
		for (Concept topLevel : ROOT_CONCEPT.getDescendants(IMMEDIATE_CHILD)) {
			Set<Concept> descendants = topLevel.getDescendants(NOT_SET);
			for (Concept thisDescendent : descendants) {
				validSemTags.add(SnomedUtilsBase.deconstructFSN(thisDescendent.getFsn())[1]);
			}
		}
		additionalReportColumns =  "EffectiveTime,Proposed SemTag,Issue,Last Known Position,Historical Relationships,, ,";
		super.postInit();
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		String semTag = SnomedUtilsBase.deconstructFSN(c.getFsn())[1];
		if (c.getId().equals("138297002")) {
			LOGGER.debug("here");
		}
		boolean isReplacement = false;
		String replacementSemTag = "";
		if (knownReplacements.containsKey(semTag)) {
			replacementSemTag = knownReplacements.get(semTag);
			isReplacement = true;
		} else {
			//What are the valid replacements for this semtag?
			Set<String> replacementSemTags = getAssocSemTags(c);
			if (replacementSemTags.size() == 1) {
				replacementSemTag = replacementSemTags.iterator().next();
			} else if (replacementSemTags.size() > 1) {
				//If we have a disorder and a finding, pick the finding
				if (replacementSemTags.contains("(disorder)") && replacementSemTags.contains("(finding)")) {
					replacementSemTag = "(finding)";
				} else {
					String replacementsString = replacementSemTags.stream().collect(Collectors.joining(","));
					report(t,c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, c.getEffectiveTime(), "Can't pick between: " + replacementsString);
					return NO_CHANGES_MADE;
				}
			}
		}
		String isA = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, IS_A, ActiveState.BOTH)
				.stream()
				.map(Relationship::toString)
				.collect(Collectors.joining(",\n"));
		String histAssocs = SnomedUtils.prettyPrintHistoricalAssociations(c, gl);
		changesMade += replaceSemTag(t, c, semTag, replacementSemTag, isReplacement);
		report(t,c, Severity.NONE, ReportActionType.INFO, c.getEffectiveTime(), replacementSemTag, isA, histAssocs);
		return changesMade;
	}

	private int replaceSemTag(Task t , Concept c, String semTag, String replacementSemTag, boolean isReplacement) throws TermServerScriptException {
		String newFSN = c.getFsn();
		if (isReplacement) {
			newFSN = newFSN.replace(semTag, replacementSemTag);
		} else {
			newFSN += " " + replacementSemTag;
		}
		replaceDescription(t, c, c.getFSNDescription(), newFSN, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
		return CHANGE_MADE;
	}

	private Set<String> getAssocSemTags(Concept c) throws TermServerScriptException {
		Set<String> replacementSemTags = new HashSet<>();
		for (AssociationEntry assoc : c.getAssociationEntries())  {
			if (assoc.isActiveSafely()) {
				Concept target = gl.getConcept(assoc.getTargetComponentId());
				replacementSemTags.add(SnomedUtils.deconstructFSN(target.getFsn())[1]);
			}
		}
		return replacementSemTags;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> process = new ArrayList<>();
		nextConcept:
		//Now work through all Concepts and list any that don't have an active semantic tag
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
			if (!validSemTags.contains(semTag) &&
					!historicallyAcceptableSemTags.contains(semTag)) {
				process.add(c);
			}
		}
		return process;
	}
}
