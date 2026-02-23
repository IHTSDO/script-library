package org.ihtsdo.termserver.scripting.pipeline.loinc;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ComponentAnnotationEntry;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.ihtsdo.termserver.scripting.pipeline.*;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincDetail;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincTerm;
import org.ihtsdo.termserver.scripting.pipeline.loinc.template.*;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConcept;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConceptNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ImportLoincTerms extends LoincScript implements LoincScriptConstants {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImportLoincTerms.class);

	private static final String COMMON_LOINC_COLUMNS = "COMPONENT, PROPERTY, TIME_ASPCT, SYSTEM, SCALE_TYP, METHOD_TYP, CLASS, CLASSTYPE, VersionLastChanged, CHNG_TYPE, STATUS, STATUS_REASON, STATUS_TEXT, ORDER_OBS, LONG_COMMON_NAME, COMMON_TEST_RANK, COMMON_ORDER_RANK, COMMON_SI_TEST_RANK, PanelType, , , , , ";
	//-f "G:\My Drive\018_Loinc\2023\LOINC Top 100 - loinc.tsv" 
	//-f1 "G:\My Drive\018_Loinc\2023\LOINC Top 100 - Parts Map 2023.tsv"  
	//-f2 "G:\My Drive\018_Loinc\2023\LOINC Top 100 - LoincPartLink_Primary.tsv"
	//-f3 "C:\Users\peter\Backup\Loinc_2.73\AccessoryFiles\PartFile\Part.csv"
	//-f4 "C:\Users\peter\Backup\Loinc_2.73\LoincTable\Loinc.csv"
	//-f5 "G:\My Drive\018_Loinc\2023\Loinc_Detail_Type_1_2.75_Active_Lab_NonVet.tsv"

	private final Set<UUID> existingEnGbLangRefsetIds = new HashSet<>();

	private Concept discouragementAnnotationType;

	private Map<String, LoincTemplatedConcept> fsnMap = new HashMap<>();
	
	protected String[] tabNames = new String[] {
			TAB_SUMMARY,
			TAB_LOINC_DETAIL_MAP_NOTES,
			TAB_MODELING_ISSUES,
			TAB_PROPOSED_MODEL_COMPARISON,
			TAB_MAP_ME,
			TAB_IMPORT_STATUS,
			TAB_ITEMS_OF_INTEREST,
			TAB_STATS};
	
	public static void main(String[] args) throws TermServerScriptException {
		new ImportLoincTerms().ingestExternalContent(args);
	}

	@Override
	public TemplatedConcept getAppropriateTemplate(ExternalConcept externalConcept) throws TermServerScriptException {
		//Is this a manually maintained concept?
		if (MANUALLY_MAINTAINED_ITEMS.containsKey(externalConcept.getExternalIdentifier())) {
			return LoincTemplatedConceptManuallyMaintained.create(externalConcept);
		}

		return switch (externalConcept.getProperty()) {
			case "ARat", "ArVRat", "CRat", "MRat", "NRat", "SRat", "VRat", "Sedimentation Rate" -> LoincTemplatedConceptWithProcess.create(externalConcept);
			case "RelTime", "Time", "Vel", "RelACnc", "RelVel" -> LoincTemplatedConceptWithProcessNoOutput.create(externalConcept);
			case "NFr", "MFr", "CFr", "AFr",  "SFr", "VFr" -> LoincTemplatedConceptWithRelative.create(externalConcept);
			case "ACnc", "ACnt", "Angle", "CCnc", "CCnt", "Diam", "EntCat", "EntLen", "EntMass", "EntNum", "EntSub",
				 "LaCnc", "Len", "LnCnc", "LsCnc", "Mass", "MCnc", "MCnt", "MoM", "MSCnc", "Naric", "NCnc", "Num",
				 "PPres", LOINC_PROPERTY_PRESENCE_THRESHOLD, "SCnc", "SCncDiff", "SCnt", "Sub", "Titr", "ThreshNum" ->
					LoincTemplatedConceptWithComponent.create(externalConcept);
			case "CRto", "MRto", "NRto", "Ratio", "SRto" -> LoincTemplatedConceptWithRatio.create(externalConcept);
			case "Anat", "Aper", "Color", "Disposition", "DistWidth", "EntMCnc", "EntMeanVol", "EntVol",
			     "ID", "Morph", "Osmol", "Prid", "Rden", "Source", "SpGrav", "Temp", "Type", "Visc", "Vol" ->
					createTemplateBasedOnProperties(externalConcept);
			case "Susc" -> LoincTemplatedConceptWithSusceptibility.create(externalConcept);
			case "{Measurement}" -> LoincTemplatedConceptForGrouper.create(externalConcept);
			case "Imp" -> LoincTemplatedConceptWithImpression.create(externalConcept);
			default -> TemplatedConceptNull.create(externalConcept);
		};
	}

	private TemplatedConcept createTemplateBasedOnProperties(ExternalConcept externalConcept) throws TermServerScriptException {
		if (externalConcept.getExternalIdentifier().equals(ContentPipelineManager.DUMMY_EXTERNAL_IDENTIFIER)) {
			//Pick one just to say that this type is "in scope".
			return LoincTemplatedConceptWithInheres.create(externalConcept);
		}

		//We would normally check a detail from within the templated concept itself
		Map<String, LoincDetail> loincDetailMap = loincDetailMapOfMaps.get(externalConcept.getExternalIdentifier());
		if (loincDetailMap != null) {
			if (loincDetailMap.containsKey("COMPNUM_PN")) {
				String partNum = loincDetailMap.get("COMPNUM_PN").getPartNumber();
				if (partNum.equals(LOINC_PART_OBSERVATION)) {
					return LoincTemplatedConceptWithInheresNoComponent.create(externalConcept);
				} else {
					return LoincTemplatedConceptWithInheres.create(externalConcept);
				}
			} else {
				LOGGER.warn("No Component part found for {}.  Using Inheres template.", externalConcept.getExternalIdentifier());
				return LoincTemplatedConceptWithInheres.create(externalConcept);
			}
		}
		throw new TermServerScriptException("No detail map found for " + externalConcept.getExternalIdentifier());
	}

	@Override
	protected String[] getTabNames() {
		return tabNames;
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Item, Info, Details, ,",
				"LoincPartNum, LoincPartName, PartType, ColumnName, Part Status, SCTID, FSN, Priority Index, Usage Count, Top Priority Usage, Mapping Notes,",
				"LoincNum, Item of Special Interest, LoincName, Template, Issues, details",
				"LoincNum, SCTID, This Iteration, Template, Differences, Proposed Descriptions, Previous Descriptions, Proposed Model, Previous Model, "  + COMMON_LOINC_COLUMNS,
				"PartNum, PartName, PartType, Needed for High Usage Mapping, Needed for Highest Usage Mapping, PriorityIndex, Usage Count,Top Priority Usage, Higest Rank, HighestUsageCount",
				"Concept, FSN, SemTag, Severity, Action, LoincNum, Descriptions, Expression, Status, , ",
				"Category, LoincNum, Detail, , , ",
				"Property, In Scope, Included, Included in Top 2K, Excluded, Excluded in Top 2K"
		};

		postInit(tabNames, columnHeadings);
		scheme = gl.getConcept(SCTID_LOINC_SCHEMA);
		externalContentModuleId = SCTID_LOINC_EXTENSION_MODULE;
		namespace = "1010000";
		discouragementAnnotationType = gl.getConcept("665161010000107 |LOINC comment (attribute)| ", false, true);
		getReportManager().disableTab(getTab(TAB_IMPORT_STATUS));

		ITEMS_OF_INTEREST.addAll(List.of("882-1","881-3","61151-7","1751-7","9318-7","1759-0","33037-3","41276-7","10466-1",
				"5767-9","33511-7","5769-5","11555-0","24321-2","1968-7","925-8","933-2","936-5",
				"62292-8","14155-6","9830-1","9322-9","2106-3","14979-9","5902-2","6301-6","38875-1",
				"50553-7","24323-8","35591-7","49024-3","39004-7","5787-7","11277-1","33219-7","12258-0",
				"788-0","30384-2","30385-9","21000-5","785-6","28539-5","786-4","28540-3","787-2",
				"30428-7","6742-1","4537-7","30341-2","58413-6","19048-8","27353-2","53553-4","49541-6",
				"48058-2","33914-3","77147-7","62238-1","48643-1","48642-3","51584-1","53115-2","34165-1",
				"38518-7","71695-1","4544-3","31100-1","56888-1","2500-7","2502-3","2532-0","24318-8",
				"26485-3","53797-7","664-3","2695-5","2708-6","32623-1","28542-9","2890-2","8251-1",
				"2965-2","5811-5","50562-8","53326-5","66746-9","3097-3","44734-2"));

		HARDCODED_DROP_OUT.addAll( List.of("53564-1", "53563-3", "53565-8"));  //Last 3 duplicate with 5992-3

		MANUALLY_MAINTAINED_ITEMS.putAll(Map.of(
				"8251-1", "580221010000109 |Service comment (observable entity)|",
				"49024-3", "580231010000107 |Differential cell count method - Blood (observable entity)|",
				"49541-6", "580241010000104 |Fasting status - Reported (observable entity)|",
				"14155-6", "580261010000100 |Cholesterol in LDL [Percentile] (observable entity)|",
				"9322-9", "580251010000102 |Cholesterol.total/Cholesterol in HDL [Percentile] (observable entity)|",
				"56888-1", "570211010000106 |Presence of HIV 1/2 Ab and/or p24 Ag (observable entity)|",
				"24318-8", "513641010000108 |Manual Differential panel - Blood (observable entity)|"
		));
	}

	@Override
	protected void loadSupportingInformation() throws TermServerScriptException {
		super.loadSupportingInformation();
		loadLoincDetail();
		loadPanels();
	}

	@Override
	protected void importPartMap() throws TermServerScriptException {
		attributePartMapManager = new LoincAttributePartMapManager(this, partMap, partMapNotes);
		attributePartMapManager.populatePartAttributeMap(getInputFile(FILE_IDX_LOINC_PARTS_MAP_BASE_FILE));
		LoincTemplatedConcept.initialise(this, loincDetailMapOfMaps);
	}

	@Override
	protected void doModeling() throws TermServerScriptException {
		for (String loincNum : loincDetailMapOfMaps.keySet()) {
			TemplatedConcept templatedConcept = modelExternalConcept(loincNum);
			if (templatedConcept != null) {
				postModelling(templatedConcept);
				if (conceptSufficientlyModeled("Observable", loincNum, templatedConcept)) {
					successfullyModelled.add(templatedConcept);
				}
			}
		}

		for (String panelLoincNum : panelLoincNums) {
			LoincTemplatedConcept templatedConcept = doPanelModeling(panelLoincNum);
			if (templatedConcept != null) {
				postModelling(templatedConcept);
				if (conceptSufficientlyModeled("Panel", panelLoincNum, templatedConcept)) {
					successfullyModelled.add(templatedConcept);
				}
			}

		}
	}

	private LoincTemplatedConcept doPanelModeling(String panelLoincNum) throws TermServerScriptException {
		//Don't do objectionable word check on panels - 'panel' is our only current objectionable word!
		if (!confirmExternalIdentifierExists(panelLoincNum)) {
			return null;
		}

		ExternalConcept panelTerm = getLoincTerm(panelLoincNum);
		return LoincTemplatedConceptPanel.create(panelTerm);
	}

	@Override
	protected void determineChangeSet() throws TermServerScriptException {

		//We've decided not to do an en-gb language refset for now, so existing content we'll set to inactive
		//and newly created en-gb we'll just delete and not put forward
		for (TemplatedConcept tc : successfullyModelled) {
			removeEnGbLangRefsets(tc.getConcept());
			removeEnGbLangRefsets(tc.getExistingConcept());
		}
		super.determineChangeSet();
	}

	private void removeEnGbLangRefsets(Concept c) {
		if (c == null) {
			return;
		}

		for (Description d : c.getDescriptions()) {
			for (LangRefsetEntry lre : d.getLangRefsetEntries(ActiveState.ACTIVE, GB_ENG_LANG_REFSET)) {
				if (existingEnGbLangRefsetIds.contains(UUID.fromString(lre.getId()))) {
					lre.setActive(false);
					incrementSummaryCount(ContentPipelineManager.LANG_REFSET_REMOVAL, "En-gb lang refset inactivated");
				} else {
					d.getLangRefsetEntries().remove(lre);
					incrementSummaryCount(ContentPipelineManager.LANG_REFSET_REMOVAL, "En-gb lang refset deleted");
				}
			}
		}
	}

	@Override
	protected void preModelling() throws TermServerScriptException {
		//Temporarily, we're going to cache a list of LOINC en-gb langrefset UUIDs
		//So that - before we examine the change set, we can inactivate or remove them
		Map<String, String> altIdentifierMap = gl.getSchemaMap(scheme);
		LOGGER.info("Noting existing en-gb lang refsets");
		for (String existingSCTID : altIdentifierMap.values()) {
			Concept c = gl.getConcept(existingSCTID);
			for (Description d : c.getDescriptions()) {
				for (LangRefsetEntry lre : d.getLangRefsetEntries(ActiveState.ACTIVE, GB_ENG_LANG_REFSET)) {
					existingEnGbLangRefsetIds.add(UUID.fromString(lre.getId()));
				}
			}
		}
		LOGGER.info("Stored {} existing en-gb lang refsets", existingEnGbLangRefsetIds.size());
	}

	@Override
	protected void postModelling(TemplatedConcept tc) throws TermServerScriptException {
		super.postModelling(tc);

		if ((tc instanceof LoincTemplatedConcept ltc
				&& !ltc.hasProcessingFlag(ProcessingFlag.DROP_OUT))) {
			checkForOrdObsRefsets(ltc);
			checkForDiscouragement(ltc);
			checkForDuplicateFSN(ltc);
		}
	}

	private void checkForOrdObsRefsets(LoincTemplatedConcept ltc) {
		LoincTerm loincTerm = ltc.getLoincTerm();
		switch (loincTerm.getOrderObs()) {
			case "Order" -> addToSimpleRefset(ltc, ORD_REFSET);
			case "Observation" -> addToSimpleRefset(ltc, OBS_REFSET);
			case "Both" -> {
				addToSimpleRefset(ltc, ORD_REFSET);
				addToSimpleRefset(ltc, OBS_REFSET);
			}
			default -> {
				//Do nothing
			}
		}
	}

	private void addToSimpleRefset(LoincTemplatedConcept ltc, Concept refset) {
		//At this point in the process we haven't connected back to any existing concept.
		//So we'll add the concept to the refset, and the reconcile later if the existing
		//concept is already in there, or if changes need to be made
		RefsetMember rm = new RefsetMember();
		rm.setModuleId(externalContentModuleId);
		rm.setReferencedComponentId(ltc.getConcept().getId());
		rm.setActive(true, true);
		rm.setRefsetId(refset.getId());
		rm.setId(UUID.randomUUID().toString());
		rm.setDirty();
		ltc.getConcept().addOtherRefsetMember(rm);
	}

	private void checkForDiscouragement(LoincTemplatedConcept ltc) {
		LoincTerm loincTerm = ltc.getLoincTerm();
		if (loincTerm.getStatus().equals("DISCOURAGED") && ltc.getConcept().getComponentAnnotationEntries().isEmpty()) {
			ComponentAnnotationEntry cae = ComponentAnnotationEntry.withDefaults(ltc.getConcept(), discouragementAnnotationType, "Discouraged");
			cae.setModuleId(RF2Constants.SCTID_LOINC_EXTENSION_MODULE);
			ltc.getConcept().addComponentAnnotationEntry(cae);
		}
	}

	private void checkForDuplicateFSN(LoincTemplatedConcept ltc) throws TermServerScriptException {
		//Check if we have already created a concept with this FSN in the fsnMap
		String fsn = ltc.getConcept().getFsn();
		if (fsnMap.containsKey(fsn)) {
			LoincTemplatedConcept existingLtc = fsnMap.get(fsn);
			if (!existingLtc.getExternalIdentifier().equals(ltc.getExternalIdentifier())) {
				LOGGER.warn("Duplicate FSN found: {} for {} and {}.  Modifying FSNs", fsn, ltc.getExternalIdentifier(), existingLtc.getExternalIdentifier());
				String newFSN = ltc.addScaleToFsn();
				String newFsnForExisting = existingLtc.addScaleToFsn();
				if (newFSN.equals(newFsnForExisting)) {
					LOGGER.warn("Unable to resolve FSN duplication {} vs {}", ltc, existingLtc);
					incrementSummaryCount("FSN Issues Encountered", "Duplicate FSN Pairs Unresolved");
					report(getTab(TAB_ITEMS_OF_INTEREST),
							"FSN Duplication unresolvable",
							ltc.getExternalIdentifier(),
							ltc,
							existingLtc);
				} else {
					fsnMap.put(newFSN, ltc);
					fsnMap.put(newFsnForExisting, existingLtc);
					fsnMap.remove(fsn);
					incrementSummaryCount("FSN Issues Encountered", "Duplicate FSN Pairs Resolved");
					report(getTab(TAB_ITEMS_OF_INTEREST),
							"FSN Duplication resolved using scale",
							ltc.getExternalIdentifier(),
							ltc,
							existingLtc);
				}
			}
		} else {
			fsnMap.put(fsn, ltc);
		}
	}
}
