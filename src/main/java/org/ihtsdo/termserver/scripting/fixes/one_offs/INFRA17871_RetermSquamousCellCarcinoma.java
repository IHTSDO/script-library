package org.ihtsdo.termserver.scripting.fixes.one_offs;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.AcceptabilityMode;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("java:S101")
public class INFRA17871_RetermSquamousCellCarcinoma extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(INFRA17871_RetermSquamousCellCarcinoma.class);

	private static final List<DescriptionType> TYPES_OF_INTEREST = List.of(DescriptionType.FSN);

	private String findText;
	private String excludeText;
	private String replaceText;

	Map<String, Concept> currentFSNs = new HashMap<>();

	protected INFRA17871_RetermSquamousCellCarcinoma(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		INFRA17871_RetermSquamousCellCarcinoma fix = new INFRA17871_RetermSquamousCellCarcinoma(null);
		try {
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.reportNoChange = true;
			fix.selfDetermining = true;
			fix.runStandAlone = true;
			fix.taskSize = 40;
			fix.taskPrefix = "INFRA-17871";
			fix.init(args);
			fix.loadProjectSnapshot();
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId(GFOLDER_ADHOC_UPDATES);
		subsetECL = "* : * = 1162767002 |Squamous cell carcinoma (morphologic abnormality)|";

		findText = "squamous cell carcinoma";
		excludeText = "squamous cell carcinomata";
		replaceText = "malignant squamous cell carcinoma";

		LOGGER.info("Obtaining all current FSNs to avoid creating a new duplicate...");
		currentFSNs = gl.getAllConcepts().stream()
				.collect(Collectors.toMap(Concept::getFsn, Function.identity(), (existing, replacement) -> existing));
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = reterm(task, loadedConcept);
			if (changesMade > 0) {
				addAcceptableDescription(task, loadedConcept);
				updateConcept(task, loadedConcept, info);
			}
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}

	private int reterm(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : c.getDescriptions(ActiveState.ACTIVE, TYPES_OF_INTEREST)) {
			if (d.isPreferred()) {
				changesMade += retermDescriptionIfRequired(t, c, d, findText, replaceText);
			}
		}
		return changesMade;
	}

	private int retermDescriptionIfRequired(Task t, Concept c, Description d, String find, String replace) throws TermServerScriptException {
		String termLower = d.getTerm().toLowerCase();
		if (termLower.contains(find) && (excludeText == null || !termLower.contains(excludeText))) {
			String replacement = determineReplacementTerm(d, find, replace);

			//If the replacement is a known FSN, we'll skip
			if (currentFSNs.containsKey(replacement)) {
				report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Replacement is a known FSN: " + replacement, currentFSNs.get(replacement));
				return NO_CHANGES_MADE;
			}
			if (!d.isReleasedSafely()) {
				report(t, c, Severity.MEDIUM, ReportActionType.INFO, "New description this cycle");
			}
			replaceDescription(t, c, d, replacement, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY, false, "", null);
			return CHANGE_MADE;
		}
		return NO_CHANGES_MADE;
	}

	private void addAcceptableDescription(Task t, Concept c) throws TermServerScriptException {
		String term = SnomedUtilsBase.deconstructFSN(c.getFsn())[0];

		Description d = Description.withDefaults(term, DescriptionType.SYNONYM, SnomedUtils.createAcceptabilityMap(AcceptabilityMode.ACCEPTABLE_BOTH));
		d.setCaseSignificance(c.getFSNDescription().getCaseSignificance());
		d.setConceptId(c.getConceptId());
		addDescription(t, c, d, false);
	}

	private String determineReplacementTerm(Description d, String find, String replace) {
		String replacement = d.getTerm().replaceAll(find, replace);

		//If the term is unchanged, try an upper case replacement
		if (replacement.equals(d.getTerm())) {
			replacement = d.getTerm().replaceAll(StringUtils.capitalizeFirstLetter(find), StringUtils.capitalizeFirstLetter(replace));
		}
		return replacement;
	}
	
	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> toProcess = new ArrayList<>();
		for (Concept c : SnomedUtils.sort(findConcepts(subsetECL, true, true))) {
			//Flag up any descriptions that have both the find AND the replace text in the same term.
			for (Description d : c.getDescriptions(ActiveState.ACTIVE, TYPES_OF_INTEREST)) {
				if (checkDescriptionForInclusion(c, d)) {
					toProcess.add(c);
					break;
				}
			}
		}
		return toProcess;
	}

	private boolean checkDescriptionForInclusion(Concept c, Description d) throws TermServerScriptException {
		String termLower = d.getTerm().toLowerCase();
		if (!matchesInclusionCriteria(d, termLower)) {
			report((Task) null, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Term does not match inclusion criteria", d);
			return false;
		}
		if (termLower.contains(findText)) {
			if (termLower.contains(replaceText)) {
				report((Task) null, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Term contains both '" + findText + "' and '" + replaceText + "'", d);
				return false;
			}
			return true;
		}
		return false;
	}

	private boolean matchesInclusionCriteria(Description d, String termLower) {
		if (d.getType().equals(DescriptionType.TEXT_DEFINITION) || !d.isPreferred()) {
			return false;
		}
		return termLower.contains(findText) && (excludeText == null || !termLower.contains(excludeText));
	}
}
