package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Combination of DRUGS-363 to remove "/1 each" from preferred terms
 * DRUGS-461
 * DRUGS-486 - MP PTs must end in "product"
 * DRUGS-492 - CDs missing "precisely"
 * DRUGS-514 - Editorial Guide updated for MPFs eg "-containing"
 * DRUGS-560 - Editorial Guide updated for MPs eg "-containing"
 * DRUGS-562 - Editorial Guide updated for Structure and Disposition Groupers
 * DRUGS-786 - Batch terming update
 */
public class NormalizeDrugTerms extends DrugBatchFix implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(NormalizeDrugTerms.class);

	private final List<String> exceptions = new ArrayList<>();

	protected NormalizeDrugTerms(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		NormalizeDrugTerms fix = new NormalizeDrugTerms(null);
		try {
			ReportSheetManager.targetFolderId="1E6kDgFExNA9CRd25yZk_Y7l-KWRf8k6B"; //Drugs/Normalize Terming
			fix.populateEditPanel = true;
			fix.populateTaskDescription = true;
			fix.selfDetermining = true;
			fix.init(args);
			fix.loadProjectSnapshot(); //Load all descriptions
			fix.postInit();
			fix.subHierarchyStr = MEDICINAL_PRODUCT.getConceptId();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		DrugUtils.populateConceptType(loadedConcept);

		int changesMade = termGenerator.ensureTermsConform(task, loadedConcept, CharacteristicType.INFERRED_RELATIONSHIP);
		if (changesMade > 0) {
			updateConcept(task, loadedConcept, info);
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		LOGGER.debug("Identifying concepts to process");
		termGenerator.setQuiet(true);
		
		List<Concept> allAffected = new ArrayList<>();
		Set<Concept> selection = gl.getConcept(subHierarchyStr).getDescendants(NOT_SET);
		for (Concept c : selection) {
			DrugUtils.populateConceptType(c);
			//Clone the concept so we're not modifying our local copy
			c = c.cloneWithIds();  //Exact copy - keep Ids
			if (isMP(c) || isMPF(c) || isCD(c)) {
				if (exceptions.contains(c.getId())) {
					report((Task)null, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Concept manually listed as an exception");
				} else {
					//See if the modifying the term makes any changes
					if (termGenerator.ensureTermsConform(null, c, CharacteristicType.INFERRED_RELATIONSHIP) > 0) {
						allAffected.add(c);
					}
				}
			}
		}
		LOGGER.info("Identified {} concepts to process", allAffected.size());
		termGenerator.setQuiet(false);
		allAffected.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<>(allAffected);
	}

	private boolean isMP(Concept concept) {
		return concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT) || 
				concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_ONLY);
	}
	
	private boolean isMPF(Concept concept) {
		return concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM) || 
				concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM_ONLY);
	}
	
	private boolean isCD(Concept concept) {
		return concept.getConceptType().equals(ConceptType.CLINICAL_DRUG);
	}
}
