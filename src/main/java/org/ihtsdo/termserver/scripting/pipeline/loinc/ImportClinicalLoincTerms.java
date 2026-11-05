package org.ihtsdo.termserver.scripting.pipeline.loinc;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincTerm;
import org.ihtsdo.termserver.scripting.pipeline.loinc.template.LoincTemplatedConceptClinicalMap;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConcept;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConceptNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Clinical LOINC terms (CLASSTYPE 2) are not modelled.  Instead the Clinical map identifies the existing
 * SNOMED CT concept that each term is equivalent to, and we add the LOINC alternate identifier and LOINC
 * descriptions to that concept.  Run separately from ImportLoincTerms, which handles the Lab terms.
 */
public class ImportClinicalLoincTerms extends LoincScript {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImportClinicalLoincTerms.class);

	private static final String CLINICAL_MAP = "Clinical Map";

//	-f1 "/Users/peter/GDrive/018_Loinc/2026/2.84/Loinc.csv"
//	-f2 "/Users/peter/GDrive/018_Loinc/2026/2.84/Part.csv"
//	-f4 "/Users/peter/GDrive/018_Loinc/2026/2.84/LOINC Clinical Concepts to SNOMED map_0.0.1.tsv"

	private TermConceptMapManager termConceptMapManager;

	protected String[] tabNames = new String[] {
			TAB_SUMMARY,
			TAB_MAP_ISSUES,
			TAB_MODELING_ISSUES,
			TAB_PROPOSED_MODEL_COMPARISON,
			TAB_IMPORT_STATUS,
			TAB_STATS};

	public static void main(String[] args) throws TermServerScriptException {
		new ImportClinicalLoincTerms().ingestExternalContent(args);
	}

	@Override
	protected String[] getTabNames() {
		return tabNames;
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Item, Info, Details, ,",
				"Line, LoincNum, Issue, , ",
				"LoincNum, Item of Interest, LoincName, Template, Issues, details",
				"LoincNum, Item of Interest, SCTID, This Iteration, Template, Differences, Proposed Descriptions, Previous Descriptions, Proposed Model, Previous Model, "  + COMMON_LOINC_COLUMNS,
				"Concept, FSN, SemTag, Severity, Action, LoincNum, Descriptions, Expression, Status, , ",
				"Property, In Scope, Included, Included in Top 2K, Excluded, Excluded in Top 2K"
		};

		postInit(tabNames, columnHeadings);
		scheme = gl.getConcept(SCTID_LOINC_SCHEMA);
		externalContentModuleId = SCTID_LOINC_EXTENSION_MODULE;
		namespace = "1010000";
		getReportManager().disableTab(getTab(TAB_IMPORT_STATUS));
	}

	@Override
	protected void loadSupportingInformation() throws TermServerScriptException {
		super.loadSupportingInformation();
		termConceptMapManager = new TermConceptMapManager(this);
		termConceptMapManager.populateConceptMap(getInputFile(FILE_IDX_LOINC_CONCEPT_MAP_FILE));
	}

	@Override
	protected void importPartMap() {
		//Clinical terms are mapped to whole concepts, so there's no Part map
	}

	@Override
	public TemplatedConcept getAppropriateTemplate(ExternalConcept externalConcept) {
		//Templates are chosen by the map, not by property
		return TemplatedConceptNull.create(externalConcept);
	}

	@Override
	protected void doModeling() throws TermServerScriptException {
		Map<String, String> altIdentifierMap = gl.getSchemaMap(scheme);
		List<Map.Entry<LoincTerm, Concept>> sortedMap = termConceptMapManager.getConceptMap().entrySet().stream()
				.sorted(Comparator.comparing(e -> e.getKey().getLoincNum()))
				.toList();

		for (Map.Entry<LoincTerm, Concept> entry : sortedMap) {
			LoincTerm loincTerm = entry.getKey();
			Concept target = entry.getValue();
			if (!hasConflictingAlternateIdentifier(loincTerm, target, altIdentifierMap)) {
				successfullyModelled.add(LoincTemplatedConceptClinicalMap.create(loincTerm, target));
				incrementSummaryCount(CONTENT_COUNT, "Content added - " + CLINICAL_MAP);
			}
		}
	}

	private boolean hasConflictingAlternateIdentifier(LoincTerm loincTerm, Concept target, Map<String, String> altIdentifierMap) throws TermServerScriptException {
		String loincNum = loincTerm.getLoincNum();
		String existingSCTID = altIdentifierMap.get(loincNum);
		if (existingSCTID != null && !existingSCTID.equals(target.getId())) {
			reportConflict(loincNum, "LOINC code already identifies " + gl.getConcept(existingSCTID) + ", but map says " + target);
			return true;
		}

		if (target.hasAlternateIdentifier(SCTID_LOINC_SCHEMA)) {
			String existingCode = target.getAlternateIdentifierForScheme(SCTID_LOINC_SCHEMA).getAlternateIdentifier();
			if (!existingCode.equals(loincNum)) {
				reportConflict(loincNum, target + " already carries LOINC code " + existingCode);
				return true;
			}
		}
		return false;
	}

	private void reportConflict(String loincNum, String detail) throws TermServerScriptException {
		LOGGER.error("{}: {}", loincNum, detail);
		report(getTab(TAB_MAP_ISSUES), "", loincNum, "CRITICAL - LOINC alternate identifier conflict, term skipped: " + detail);
		incrementSummaryCount(CLINICAL_MAP, "CRITICAL - LOINC alternate identifier conflict");
	}

	@Override
	protected void determineChangeSet() throws TermServerScriptException {
		//The generic comparison normalises every component to the LOINC module, which would re-home
		//International content.  Here we only ever output what we add.
		List<TemplatedConcept> sortedModelled = successfullyModelled.stream()
				.sorted(Comparator.comparing(TemplatedConcept::getExternalIdentifier))
				.toList();

		for (TemplatedConcept tc : sortedModelled) {
			determineChanges((LoincTemplatedConceptClinicalMap) tc);
		}

		reportWithdrawnMaps(sortedModelled);
	}

	private void determineChanges(LoincTemplatedConceptClinicalMap tc) throws TermServerScriptException {
		Concept concept = tc.getConcept();
		boolean altIdExisted = tc.addAlternateIdentifier(externalContentModuleId);
		int descriptionsAdded = tc.addLoincDescriptions(externalContentModuleId);
		for (Description d : concept.getDescriptions()) {
			if (d.isDirty()) {
				conceptCreator.populateComponentId(concept, d, externalContentModuleId);
			}
		}

		if (!altIdExisted || descriptionsAdded > 0) {
			tc.setIterationIndicator(TemplatedConcept.IterationIndicator.MODIFIED);
			String altIdChange = altIdExisted ? "" : "Alternate identifier added. ";
			tc.recordDifferenceFromExistingConcept(altIdChange + descriptionsAdded + " description(s) added");
			conceptCreator.outputRF2(getTab(TAB_IMPORT_STATUS), concept, "");
		} else {
			tc.setIterationIndicator(TemplatedConcept.IterationIndicator.UNCHANGED);
			tc.recordDifferenceFromExistingConcept("All Unchanged");
		}
		incrementSummaryCount(CHANGES_SINCE_LAST_ITERATION, tc.getIterationIndicator().toString());
		doProposedModelComparison(tc);
	}

	private void reportWithdrawnMaps(List<TemplatedConcept> sortedModelled) throws TermServerScriptException {
		//First pass: flag withdrawn maps, but leave the alternate identifier and descriptions in place
		Set<String> mapped = new HashSet<>();
		sortedModelled.forEach(tc -> mapped.add(tc.getExternalIdentifier()));
		Map<String, String> altIdentifierMap = new TreeMap<>(gl.getSchemaMap(scheme));
		for (Map.Entry<String, String> entry : altIdentifierMap.entrySet()) {
			LoincTerm loincTerm = getLoincTerm(entry.getKey());
			if (loincTerm != null
					&& LOINC_CLASSTYPE_CLINICAL.equals(loincTerm.getClassType())
					&& !mapped.contains(entry.getKey())) {
				report(getTab(TAB_MAP_ISSUES), "", entry.getKey(), "Map withdrawn - alternate identifier and descriptions left in place on " + gl.getConcept(entry.getValue()));
				incrementSummaryCount(CLINICAL_MAP, "Map withdrawn - left in place");
			}
		}
	}

}
