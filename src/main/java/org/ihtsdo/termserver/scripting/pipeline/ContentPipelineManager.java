package org.ihtsdo.termserver.scripting.pipeline;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.AxiomUtils;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.delta.Rf2ConceptCreator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConceptNull;
import org.ihtsdo.termserver.scripting.pipeline.domain.Part;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincTerm;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConcept;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConceptNull;
import org.ihtsdo.termserver.scripting.util.ComponentComparisonHelper;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ContentPipelineManager extends TermServerScript implements ContentPipeLineConstants {

	public static final String CHANGES_SINCE_LAST_ITERATION = "Changes since last iteration";
	public static final String HIGH_USAGE_COUNTS = "High usage counts";
	public static final String HIGHEST_USAGE_COUNTS = "Highest usage counts";
	public static final String CONTENT_COUNT = "Content counts";
	public static final String INTERNAL_MAP_COUNT = "Internal map counts";
	public static final String REFSET_COUNT = "Refset counts";
	public static final String FAILED_TO_LOAD = "Failed to load ";
	public static final String LANG_REFSET_REMOVAL = "Lang Refset Removal";

	public static final String DUMMY_EXTERNAL_IDENTIFIER = "DUMMY_EXTERNAL_IDENTIFIER";
	
	public static final String FSN_FAILURE = "FSN indicates failure";

	// Regular expression to find tokens within square brackets
	private static final String ALL_CAPS_SLOT_REGEX = "\\[([A-Z]+)\\]";
	private static final Pattern allCapsSlotPattern = Pattern.compile(ALL_CAPS_SLOT_REGEX);

	protected String primaryLangRefset = US_ENG_LANG_REFSET;

	private enum RunMode { NEW, INCREMENTAL_DELTA, INCREMENTAL_API}
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ContentPipelineManager.class);
	
	protected static final RunMode runMode = RunMode.INCREMENTAL_DELTA;
	
	protected Map<String, ExternalConcept> externalConceptMap = new HashMap<>();
	protected AttributePartMapManager attributePartMapManager;
	protected Map<String, Part> partMap = new HashMap<>();
	protected Map<String, String> partMapNotes = new HashMap<>();
	protected Map<Part, Set<ExternalConcept>> missingPartMappings = new HashMap<>();
	public static final Map<String, String> ITEM_OF_INTEREST_MAP = new HashMap<>();

	protected Concept scheme;
	protected String namespace;
	protected String externalContentModuleId;
	protected Rf2ConceptCreator conceptCreator;
	protected int additionalThreadCount = 0;
	protected Set<TemplatedConcept> successfullyModelled = new HashSet<>();
	protected Set<TemplatedConcept> inactivatedConcepts = new HashSet<>();
	protected boolean includeShortNameDescription = true;
	protected boolean includeShortNameAsPreferredTerm = false;
	protected boolean includeLongNameDescription = false;

	protected Set<ComponentType> skipForComparison = Set.of(
			ComponentType.INFERRED_RELATIONSHIP,
			ComponentType.LANGREFSET);

	protected List<TemplatedConcept.IterationIndicator> activeIndicators = List.of(
			TemplatedConcept.IterationIndicator.NEW,
			TemplatedConcept.IterationIndicator.UNCHANGED,
			TemplatedConcept.IterationIndicator.MODIFIED,
			TemplatedConcept.IterationIndicator.RESURRECTED);

	protected void ingestExternalContent(String[] args) throws TermServerScriptException {
		try {
			runStandAlone = false;
			getGraphLoader().setExcludedModules(new HashSet<>());
			getArchiveManager().setLoadOtherReferenceSets(true);
			getArchiveManager().setRunIntegrityChecks(false);
			getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);  //Needed for working out if we're deleteing or inactivating
			init(args);
			loadProjectSnapshot(false);
			postInit();
			conceptCreator = Rf2ConceptCreator.build(this, args);
			conceptCreator.initialiseDeltaGeneratorSpecifics(new String[]{"-nS",this.getNamespace(), "-m", getExternalContentModuleId()});
			TemplatedConcept.initialise(this);
			loadSupportingInformation();
			importPartMap();
			preModelling();
			doModeling();
			TemplatedConcept.reportStats(getTab(TAB_SUMMARY));
			if (tabExists(TAB_MAP_ME)) {
				reportMissingMappings(getTab(TAB_MAP_ME));
			}
			reportIncludedExcludedConcepts(getTab(TAB_STATS));
			flushFiles(false);
			switch (runMode) {
				case NEW: outputAllConceptsToDelta();
					break;
				case INCREMENTAL_API, INCREMENTAL_DELTA:
					determineChangeSet();
					break;
				default:
					throw new TermServerScriptException("Unrecognised Run Mode :" + runMode);
			}
			reportSummaryCounts(getTab(TAB_SUMMARY));
			conceptCreator.createOutputArchive(getTab(TAB_IMPORT_STATUS));
		} finally {
			while (additionalThreadCount > 0) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			finish();
			if (conceptCreator != null) {
				conceptCreator.finish();
			}
		}
	}

	public void recordSuccessfulModelling(TemplatedConcept tc) {
		successfullyModelled.add(tc);
	}

	public boolean shouldIncludeShortNameDescription() {
		return includeShortNameDescription;
	}

	public boolean isIncludeShortNameAsPreferredTerm() {
		return includeShortNameAsPreferredTerm;
	}

	public void setIncludeShortNameAsPreferredTerm(boolean includeShortNameAsPreferredTerm) {
		this.includeShortNameAsPreferredTerm = includeShortNameAsPreferredTerm;
	}

	public boolean shouldIncludeLongNameDescription() {
		return includeLongNameDescription;
	}

	public void setIncludeLongNameDescription(boolean includeLongNameDescription) {
		this.includeLongNameDescription = includeLongNameDescription;
	}

	public String getExternalContentModuleId() {
		return externalContentModuleId;
	}

	protected void preModelling() throws TermServerScriptException {
		//Override this method in base class to do some setup prior to modeling
	}

	protected void postModelling(TemplatedConcept tc) throws TermServerScriptException {
		validateTemplatedConcept(tc);
		tc.populateAlternateIdentifier();

		//Is this an item of interest?
		if (ITEM_OF_INTEREST_MAP.containsKey(tc.getExternalIdentifier())) {
			tc.addReasonForInterest(ITEM_OF_INTEREST_MAP.get(tc.getExternalIdentifier()));
		}
	}

	private boolean tabExists(String tabName) {
		try {
			getTab(tabName);
			return true;
		} catch (TermServerScriptException e) {
			return false;
		}
	}

	protected abstract String getContentType();

	protected abstract void loadSupportingInformation() throws TermServerScriptException;

	protected abstract void importPartMap() throws TermServerScriptException;

	protected List<String> getExternalConceptsToModel() throws TermServerScriptException {
		return new ArrayList<>(externalConceptMap.keySet());
	}

	protected void doModeling() throws TermServerScriptException {
		for (String externalIdentifier : getExternalConceptsToModel()) {
			TemplatedConcept templatedConcept = modelExternalConcept(externalIdentifier);
			postModelling(templatedConcept);
			if (conceptSufficientlyModeled(getContentType(), externalIdentifier, templatedConcept)
				|| MANUALLY_MAINTAINED_ITEMS.containsKey(externalIdentifier)) {
				recordSuccessfulModelling(templatedConcept);
			}
		}
	}

	protected TemplatedConcept modelExternalConcept(String externalIdentifier) throws TermServerScriptException {

		if (externalIdentifier.equals("NPU26879")) {
			LOGGER.debug("Check Blood");
		}

		if (externalIdentifier.equals("75033-1")) {
			LOGGER.debug("Check LE-160");
		}

		ExternalConcept externalConcept = externalConceptMap.get(externalIdentifier);
		if (!confirmExternalIdentifierExists(externalIdentifier) ||
				(containsObjectionableWord(externalConcept) && !MANUALLY_MAINTAINED_ITEMS.containsKey(externalIdentifier))) {
			//Kick out things like panels, but only if we haven't said we're manually maintaining them
			return null;
		}

		//Is this a transformed concept that's being maintained manually?  Return what is already there if so.
		if (MANUALLY_MAINTAINED_ITEMS.containsKey(externalIdentifier)) {
			TemplatedConcept tc = getAppropriateTemplate(externalConcept);
			//We need to assign a clone of the concept, so that anything we do to it - like add an annotation -
			//can be detected as a change to the existing concept
			Concept originalState = gl.getConcept(MANUALLY_MAINTAINED_ITEMS.get(externalIdentifier)).cloneWithIds();
			tc.setConcept(originalState);
			tc.setIterationIndicator(TemplatedConcept.IterationIndicator.MANUAL);
			tc.populateAlternateIdentifier();
			//If we don't already have this alt identifier, we'll output it now, as we don't output changes for manually maintained items
			if (!gl.getSchemaMap(scheme).containsKey(externalIdentifier)) {
				conceptCreator.outputAltId(tc.getConcept(), scheme.getId());
			}
			return tc;
		}

		TemplatedConcept tc = getAppropriateTemplate(externalConcept);

		if (!(tc instanceof TemplatedConceptNull)) {
			try {
				tc.populateTemplate();
			} catch (TermServerScriptException e) {
				LOGGER.info("Failed to populate template for {}", externalIdentifier);
				tc.getConcept().addIssue(e.getMessage());
				tc.addProcessingFlag(ProcessingFlag.DROP_OUT);
			}
		} else if (externalConcept.isHighUsage()) {
			//This is a 'highest usage' term, which is out of scope
			incrementSummaryCount(ContentPipelineManager.HIGH_USAGE_COUNTS, "High Usage Out of Scope");
			//Is it also 'highest usage'?
			if (externalConcept.isHighestUsage()) {
				incrementSummaryCount(ContentPipelineManager.HIGHEST_USAGE_COUNTS, "Highest Usage Out of Scope");
			}
		}

		if (HARDCODED_DROP_OUT.contains(externalIdentifier)) {
			tc.addProcessingFlag(ProcessingFlag.DROP_OUT);
			tc.getConcept().addIssue("Manually specified for exclusion");
		}
		return tc;
	}

	protected abstract String[] getTabNames();

	protected abstract Set<String> getObjectionableWords();



	private void outputAllConceptsToDelta() throws TermServerScriptException {
		for (TemplatedConcept tc : successfullyModelled) {
			Concept concept = tc.getConcept();
			try {
				conceptCreator.writeConceptToRF2(NOT_SET, concept, tc.getExternalIdentifier());
			} catch (Exception e) {
				report(getTab(TAB_IMPORT_STATUS), null, concept, Severity.CRITICAL, ReportActionType.API_ERROR, tc.getExternalIdentifier(), e);
			}
		}
		conceptCreator.createOutputArchive(getTab(TAB_IMPORT_STATUS));
	}

	protected void determineChangeSet() throws TermServerScriptException {
		LOGGER.info("Determining change set for {} successfully modelled concepts", successfullyModelled.size());

		Set<String> externalIdentifiersProcessed = new HashSet<>();

		//Sort so that subsequent spreadsheets are somewhat comparable
		List<TemplatedConcept> sortedModelled = successfullyModelled.stream()
				.sorted(Comparator.comparing(TemplatedConcept::getExternalIdentifier))
				.toList();
		
		for (TemplatedConcept tc : sortedModelled) {
			incrementSummaryCount("Counts per template", tc.getClass().getSimpleName());
			determineChanges(tc, externalIdentifiersProcessed);
		}

		determineInactivations(sortedModelled);
	}

	private void determineInactivations(List<TemplatedConcept> sortedModelled) throws TermServerScriptException {
		//What external codes do we currently have that we _aren't_ going forward with.
		//Those need to be inactivated
		Map<String, String> altIdentifierMap = gl.getSchemaMap(scheme);
		Set<String> inactivatingCodes =  new HashSet<>(altIdentifierMap.keySet());
		inactivatingCodes.removeAll(sortedModelled.stream().map(m -> m.getExternalIdentifier()).collect(Collectors.toSet()));
		for (String inactivatingCode : inactivatingCodes) {
			processInactivation(inactivatingCode, altIdentifierMap);
		}
	}

	private void processInactivation(String inactivatingCode, Map<String, String> altIdentifierMap) throws TermServerScriptException {
		String existingConceptSCTID = altIdentifierMap.get(inactivatingCode);
		Concept existingConcept = gl.getConcept(existingConceptSCTID, false, false);

		boolean remainsInactive = false;
		if (existingConcept != null) {
			if (existingConcept.isActiveSafely()) {
				inactivateConcept(existingConcept);
				conceptCreator.outputRF2Inactivation(existingConcept);
			} else {
				//If the existing concept is already inactive, we just need to record that.
				remainsInactive = true;
				checkRemainsInactiveConcept(existingConcept);
			}
		}

		//Create a Templated Concept to record the inactivation
		//But, does this external code even still exist in the external code system?
		TemplatedConcept inactivation;
		ExternalConcept ec = externalConceptMap.get(inactivatingCode);
		if (ec == null) {
			LOGGER.warn("Did not find an external concept for inactivating code: {}", inactivatingCode);
			inactivation = TemplatedConceptNull.createNull(inactivatingCode, null);
		} else {
			inactivation = TemplatedConceptNull.create(ec);
		}

		if (remainsInactive) {
			inactivation.setIterationIndicator(TemplatedConcept.IterationIndicator.REMAINS_INACTIVE);
		}

		inactivation.setConcept(existingConcept);
		inactivatedConcepts.add(inactivation);

		doProposedModelComparison(inactivation);

		String iterationIndicator = remainsInactive ?
				TemplatedConcept.IterationIndicator.REMAINS_INACTIVE.toString() :
				TemplatedConcept.IterationIndicator.REMOVED.toString();
		incrementSummaryCount(CHANGES_SINCE_LAST_ITERATION, iterationIndicator);

		//Might not be obvious: the alternate identifier continues to exist even when the concept becomes inactive
		//So - temporarily again - we'll normalize the scheme id
		//Temporarily correct all Alternate Identifiers
		if (existingConcept != null) {
			existingConcept.setAlternateIdentifiers(new HashSet<>());
			existingConcept.addAlternateIdentifier(inactivatingCode, scheme.getId());
		}
	}

	private void checkRemainsInactiveConcept(Concept c) throws TermServerScriptException {
		if (c.isActiveSafely()) {
			throw new IllegalStateException("Concept " + c + " expected to be inactive but is active");
		}
		boolean changesMade = false;
		for (AxiomEntry ax : c.getAxiomEntries(ActiveState.ACTIVE, true)) {
			if (ax.isActiveSafely()) {
				ax.setActive(false);
				changesMade = true;
			}
		}

		if (changesMade) {
			conceptCreator.outputRF2Inactivation(c);
		}
	}

	private void determineChanges(TemplatedConcept tc, Set<String> externalIdentifiersProcessed) throws TermServerScriptException {
		Concept concept = tc.getConcept();
		externalIdentifiersProcessed.add(tc.getExternalIdentifier());

		//Do we already have this concept?  Also, it might use freshly modelled concepts internally which need to have IDs assigned
		//before we can compare their axioms
		Concept existingConcept = getExistingConceptAndPopulateReferencedConcepts(tc);

		//We need to make any adjustments to inferred relationships before we lose the stated ones in the transformation to axioms
		adjustInferredRelationships(concept, existingConcept);

		if (existingConcept == null) {
			//This concept is entirely new, prepare to output all
			if (runMode.equals(RunMode.INCREMENTAL_DELTA)) {
				conceptCreator.populateIds(concept);
			}

			if (tc.getIterationIndicator() == null) {
				tc.setIterationIndicator(TemplatedConcept.IterationIndicator.NEW);
			}
			convertStatedRelationshipsToAxioms(concept, true, true);
			concept.setAxiomEntries(AxiomUtils.convertClassAxiomsToAxiomEntries(concept));
			//If we're not comparing with the exisnig concept, we need to count those new annotations and refset members
			for (Component c : SnomedUtils.getAllComponents(tc.getConcept())) {
				recordRefsetMemberSummaryCount(c, TemplatedConcept.IterationIndicator.NEW);
			}
		} else {
			determineChangesWithExistingConcept(tc);
		}

		//Update the summary count based on the comparison to the previous iteration
		incrementSummaryCount(CHANGES_SINCE_LAST_ITERATION, tc.getIterationIndicator().toString());

		//Is this a high usage concept?
		if (activeIndicators.contains(tc.getIterationIndicator()) && tc.isHighUsage()) {
			incrementSummaryCount(HIGH_USAGE_COUNTS, "Active with high usage");
		}
		if (activeIndicators.contains(tc.getIterationIndicator()) && tc.isHighestUsage()) {
			incrementSummaryCount(HIGHEST_USAGE_COUNTS,"Active with highest usage");
		}
		doProposedModelComparison(tc);

		if (!tc.getIterationIndicator().equals(TemplatedConcept.IterationIndicator.UNCHANGED)) {
			conceptCreator.outputRF2(getTab(TAB_IMPORT_STATUS), tc.getConcept(), "");
		}

		if (tc.existingConceptHasInactivations()) {
			conceptCreator.outputRF2Inactivation(tc.getExistingConcept());
		}
	}

	private Concept getExistingConceptAndPopulateReferencedConcepts(TemplatedConcept tc) throws TermServerScriptException {
		Map<String, String> altIdentifierMap = gl.getSchemaMap(scheme);
		String existingConceptSCTID = altIdentifierMap.get(tc.getExternalIdentifier());

		Concept existingConcept = getExistingConceptIfExists(existingConceptSCTID, tc);
		tc.setExistingConcept(existingConcept);

		for (Relationship r : tc.getConcept().getRelationships()) {
			Concept targetValue = r.getTarget();
			//Do we have a null id, or a temporary UUID?
			if (targetValue.getConceptId() == null || targetValue.getConceptId().length() > SCTID_MAX_LENGTH) {
				//Can we find that concept via what it might have been created for?
				String targetExternalId = findExternalIdentifierForModelledConcept(targetValue);
				if (targetExternalId == null) {
					throw new TermServerScriptException("Unable to find external identifier for modelling in concept " + tc.getConcept().toExpression(CharacteristicType.STATED_RELATIONSHIP));
				} else {
					String existingTargetSCTID = altIdentifierMap.get(targetExternalId);
					Concept existingTarget = getExistingConceptIfExists(existingTargetSCTID, tc);
					if (existingTarget == null) {
						//We need to give this concept an ID before we can form an axiom
						conceptCreator.populateComponentId(targetValue,targetValue, externalContentModuleId);
					} else {
						r.setTarget(existingTarget);
					}
				}
			}
		}
		return existingConcept;
	}

	private String findExternalIdentifierForModelledConcept(Concept c) {
		for (TemplatedConcept tc : successfullyModelled) {
			if (tc.getConcept().equals(c)) {
				return tc.getExternalIdentifier();
			}
		}
		return null;
	}

	/**
	 * @return true if changes are detected
	 */
	private void determineChangesWithExistingConcept(TemplatedConcept tc) throws TermServerScriptException {
		SnomedUtils.getAllComponents(tc.getConcept()).forEach(c -> {
			c.setClean();
			//Normalise module
			c.setModuleId(conceptCreator.getTargetModuleId());
		});

		//We need to populate the concept SCTID before we can create axiom entries
		Concept c = tc.getConcept();
		String sctId = tc.getExistingConcept().getId();
		c.setId(sctId);
		//And we can apply that to the alternate identifiers early on so they don't show up as a change
		c.getAlternateIdentifiers()
				.forEach(a -> a.setReferencedComponentId(sctId));
		//Also for refset members and annotations
		c.getOtherRefsetMembers().forEach(rm -> rm.setReferencedComponentId(sctId));
		c.getComponentAnnotationEntries().forEach(a -> a.setReferencedComponentId(sctId));

		//Copy the axiom entry from the existing concept so relationship changes can be applied there
		c.setAxiomEntries(tc.getExistingConcept().getAxiomEntries(ActiveState.ACTIVE, false));
		convertStatedRelationshipsToAxioms(tc.getConcept(), true, true);
		c.setAxiomEntries(AxiomUtils.convertClassAxiomsToAxiomEntries(tc.getConcept()));

		List<ComponentComparisonResult> componentComparisonResults = ComponentComparisonHelper.compareComponents(tc.getExistingConcept(), tc.getConcept(), skipForComparison);
		if (ComponentComparisonResult.hasChanges(componentComparisonResults)) {
			if (tc.getExistingConcept().isActiveSafely()) {
				tc.setIterationIndicator(TemplatedConcept.IterationIndicator.MODIFIED);
			} else {
				//TODO Axiom not being recreated here
				tc.setIterationIndicator(TemplatedConcept.IterationIndicator.REACTIVATED);
				reactivateConcept(tc.getConcept());
			}
		} else {
			tc.setIterationIndicator(TemplatedConcept.IterationIndicator.UNCHANGED);
			tc.recordDifferenceFromExistingConcept("All Unchanged");
		}

		for (ComponentComparisonResult componentComparisonResult : componentComparisonResults) {
			processComponentComparison(tc, componentComparisonResult);
		}
	}

	private void reactivateConcept(Concept c) {
		c.setActive(true);
		//Inactivate inactivation indicators and historical associations
		c.getInactivationIndicatorEntries()
				.forEach(ii -> ii.setActive(false));
		c.getAssociationEntries()
				.forEach(a -> a.setActive(false));
		c.getAxiomEntries()
				.forEach(ax -> ax.setActive(true));
	}

	private void processComponentComparison(TemplatedConcept tc, ComponentComparisonResult componentComparisonResult) throws TermServerScriptException {
		Component existingComponent = componentComparisonResult.getLeft();
		Component newlyModelledComponent = componentComparisonResult.getRight();

		if (!componentComparisonResult.isMatch()) {
			tc.recordDifferenceFromExistingConcept(componentComparisonResult.getComponentTypeStr());
		}

		//If we have both, then just output the change
		if (existingComponent != null && newlyModelledComponent != null) {
			newlyModelledComponent.setId(existingComponent.getId());
			if (componentComparisonResult.isMatch()) {
				newlyModelledComponent.setClean();
			} else {
				newlyModelledComponent.setDirty();
			}

			//Any component specific actions?
			switch (existingComponent.getComponentType()) {
				case CONCEPT:
					alignAlternateIdentifier(tc.getConcept(), tc.getExistingConcept());
					break;
				case DESCRIPTION:
					Description newDesc = (Description)newlyModelledComponent;
					newDesc.setConceptId(tc.getExistingConcept().getId());
					alignLangRefsetEntries(newDesc, (Description)existingComponent);
					break;
				case SIMPLE_REFSET_MEMBER:
				case COMPONENT_ANNOTATION:
					alignRefsetMember(componentComparisonResult.isMatch(), (RefsetMember)newlyModelledComponent, (RefsetMember)existingComponent);
					TemplatedConcept.IterationIndicator indicator = componentComparisonResult.isMatch() ? TemplatedConcept.IterationIndicator.UNCHANGED : TemplatedConcept.IterationIndicator.MODIFIED;
					recordRefsetMemberSummaryCount(newlyModelledComponent, indicator);
					break;
				default:
					break;
			}
		} else if (existingComponent != null && newlyModelledComponent == null) {
			//If we have an existing component, and it has no newly Modelled counterpart, then inactivate it
			existingComponent.setActive(false);
			existingComponent.setDirty();
			tc.setExistingConceptHasInactivations(true);
			recordRefsetMemberSummaryCount(existingComponent, TemplatedConcept.IterationIndicator.REMOVED);
			switch (existingComponent.getComponentType()) {
				case DESCRIPTION:
					((Description)existingComponent).getLangRefsetEntries().forEach(lre -> {
						lre.setActive(false);  //Will set dirty if not already
					});
					break;
				default:
					break;
			}
		} else {
			//If we only have a newly modelled component, give it an id
			//and prepare to output
			conceptCreator.populateComponentId(tc.getExistingConcept(), newlyModelledComponent, externalContentModuleId);
			newlyModelledComponent.setDirty();
			recordRefsetMemberSummaryCount(newlyModelledComponent, TemplatedConcept.IterationIndicator.NEW);
		}

		//If we don't have an ID at this point, we've gone wrong somewhere
		if ((newlyModelledComponent != null && newlyModelledComponent.getId() == null)
				|| (existingComponent != null && existingComponent.getId() == null)) {
			throw new IllegalStateException("Component encountered without Id " + newlyModelledComponent);
		}
	}

	private void recordRefsetMemberSummaryCount(Component c, TemplatedConcept.IterationIndicator iterationIndicator) throws TermServerScriptException {
		if (c.getComponentType().equals(ComponentType.SIMPLE_REFSET_MEMBER) || c.getComponentType().equals(ComponentType.COMPONENT_ANNOTATION)) {
			Concept refset = gl.getConcept(((RefsetMember)c).getRefsetId());
			incrementSummaryCount("Refset Changes since last iteration", refset.getPreferredSynonym() + " " + iterationIndicator);
		}
	}

	private void alignRefsetMember(boolean isMatch, RefsetMember newlyModelledComponent, RefsetMember existingComponent) {
		newlyModelledComponent.setId(existingComponent.getId());
		if (isMatch) {
			newlyModelledComponent.setClean();
		} else {
			newlyModelledComponent.setDirty();
		}
	}

	private void alignAlternateIdentifier(Concept cNew, Concept cExisting) {
		//If we have the same scheme and altId, then we just need to copy over the previous memberId
		//and then we can mark the altId as clean and no need to output it again
		for (AlternateIdentifier altId : cNew.getAlternateIdentifiers()) {
			AlternateIdentifier existingAltId = cExisting.getAlternateIdentifierForScheme(altId.getIdentifierSchemeId());
			if (existingAltId != null && existingAltId.getId().equals(altId.getId())) {
				altId.setId(existingAltId.getId());
				altId.setClean();
			}
		}
	}

	private void alignLangRefsetEntries(Description newDesc, Description oldDesc) {
		//Now if the oldDesc didn't have a lang refset entry, we need to bring the new one into play
		//Might need to also handle the case where we're reactivating inactive lang refset entries
		List<LangRefsetEntry> oldLres = oldDesc.getLangRefsetEntries(ActiveState.ACTIVE);
		if (oldLres.isEmpty()) {
			for (LangRefsetEntry newLre : newDesc.getLangRefsetEntries(ActiveState.ACTIVE)) {
				//This is a new lang refset entry, we need to assign it an ID
				newLre.setReferencedComponentId(newDesc.getId());
				newLre.setDirty();
			}
		} else {
			//For each refsetId, pinch the ID from the existing description and apply it to the new one
			for (LangRefsetEntry lre : oldLres) {
				List<LangRefsetEntry> newLres = newDesc.getLangRefsetEntries(ActiveState.ACTIVE, lre.getRefsetId());
				//The new description might not have an entry for this refsetId, eg if we've removed en-gb
				if (newLres.isEmpty()) {
					//If we've removed the en-gb lang refset or similar, then we need to inactivate the existing one
					lre.setActive(false);
					lre.setDirty();
				} else {
					LangRefsetEntry newLre = newLres.get(0);
					newLre.setId(lre.getId());
					newLre.setReferencedComponentId(oldDesc.getId());
					newLre.setClean();
					//But, has the acceptability changed?  If so, we need to output this as a change
					if (!newLre.getAcceptabilityId().equals(lre.getAcceptabilityId())) {
						newLre.setDirty();
					}
				}
			}
		}
	}

	private Concept getExistingConceptIfExists(String existingConceptSCTID, TemplatedConcept tc) throws TermServerScriptException {
		Concept existingConcept = null;
		if (existingConceptSCTID != null) {
			existingConcept = gl.getConcept(existingConceptSCTID, false, false);
			if (existingConcept == null) {
				String msg = "Alternate identifier " + tc.getExternalIdentifier() + " --> " + existingConceptSCTID + " but existing concept not found.  Did it get deleted?  Reusing ID.";
				addFinalWords(msg);
				tc.getConcept().setId(existingConceptSCTID);
				tc.setIterationIndicator(TemplatedConcept.IterationIndicator.RESURRECTED);
			}
		}
		return existingConcept;
	}

	private boolean adjustInferredRelationships(Concept concept, Concept existingConcept) {
		boolean changesMade = false;
		if (existingConcept == null) {
			conceptCreator.copyStatedRelsToInferred(concept);
			changesMade = true;
		}
		return changesMade;
	}

	private List<String> inactivateConcept(Concept c) {
		List<String> differencesList = new ArrayList<>();
		//To inactivate a concept we need to inactivate the concept itself and the OWL axiom.
		//The descriptions remain active, and we'll let classification sort out the inferred relationships
		if (c.isActiveSafely()) {
			c.setActive(false);  //This will inactivate the concept and all relationships
			InactivationIndicatorEntry ii = InactivationIndicatorEntry.withDefaults(c, SCTID_INACT_OUTDATED);
			ii.setModuleId(externalContentModuleId);
			c.addInactivationIndicator(ii);
			differencesList.add("CONCEPT");
		}
		
		for (AxiomEntry a : c.getAxiomEntries(ActiveState.ACTIVE, true)) {
			a.setActive(false);
			differencesList.add("AXIOM");
		}

		for (RefsetMember rm : c.getOtherRefsetMembers()) {
			if (rm.isActiveSafely()) {
				rm.setActive(false);
				differencesList.add("REFSET_MEMBER " + rm.getRefsetId());
			}
		}
		return differencesList;
	}
	
	protected void reportIncludedExcludedConcepts(int tabIdx) throws TermServerScriptException {
		Set<String> successfullyModelledExternalIds = successfullyModelled.stream()
				.map(TemplatedConcept::getExternalIdentifier)
				.collect(Collectors.toSet());

		//Collect both included and excluded terms by property
		Map<String, List<ExternalConcept>> included = externalConceptMap.values().stream()
				.filter(lt -> successfullyModelledExternalIds.contains(lt.getExternalIdentifier()))
				.collect(Collectors.groupingBy(this::decorateProperty));

		Map<String, List<ExternalConcept>> excluded = externalConceptMap.values().stream()
				.filter(lt -> !successfullyModelledExternalIds.contains(lt.getExternalIdentifier()))
				.collect(Collectors.groupingBy(this::decorateProperty));

		Set<String> properties = new LinkedHashSet<>(included.keySet());
		properties.addAll(excluded.keySet());

		for (String property : properties) {
			int includedCount = included.getOrDefault(property, new ArrayList<>()).size();
			int includedInTop2KCount = included.getOrDefault(property, new ArrayList<>()).stream()
					.filter(ExternalConcept::isHighestUsage)
					.toList().size();
			int excludedCount = excluded.getOrDefault(property, new ArrayList<>()).size();
			int excludedInTop2KCount = excluded.getOrDefault(property, new ArrayList<>()).stream()
					.filter(ExternalConcept::isHighestUsage)
					.toList().size();
			report(tabIdx, property, inScope(undecorate(property)), includedCount, includedInTop2KCount, excludedCount, excludedInTop2KCount);
		}
	}

	private String undecorate(String property) {
		return property.split(" ")[0];
	}

	private String decorateProperty(ExternalConcept ec) {
		String decoratedProperty = ec.getProperty();
		if (ec instanceof LoincTerm lt) {
			decoratedProperty += " " + lt.getClassType();
		}
		return decoratedProperty;
	}

	public int getTab(String tabName) throws TermServerScriptException {
		String[] tabNames = getTabNames();
		for (int i = 0; i < tabNames.length; i++) {
			if (tabNames[i].equals(tabName)) {
				return i;
			}
		}
		throw new TermServerScriptException("Tab '" + tabName + "' not recognised");
	}
	
	protected String getNamespace() {
		return namespace;
	}

	public Part getPart(String partId) {
		return partMap.getOrDefault(partId, null);
	}

	public static final List<String> HARDCODED_DROP_OUT = new ArrayList<>();

	public static final Map<String, String> MANUALLY_MAINTAINED_ITEMS = new HashMap<>();

	public abstract List<String> getMappingsAllowedAbsent();
	
	protected void reportMissingMappings(int tabIdx) throws TermServerScriptException {
		for (Map.Entry<Part, Set<ExternalConcept>> entry : missingPartMappings.entrySet()) {
			Part part = entry.getKey();
			Set<ExternalConcept> externalConcepts = entry.getValue();
			String[] highUsageIndicators = getHighUsageIndicators(externalConcepts);
			report(tabIdx, 
					part.getPartNumber(),
					part.getPartName(),
					part.getPartTypeName(),
					highUsageIndicators[0],
					highUsageIndicators[1],
					"N/A",
					externalConcepts.size(),
					highUsageIndicators[2],
					highUsageIndicators[3],
					highUsageIndicators[4]);
		}
	}

	protected abstract String[] getHighUsageIndicators(Set<ExternalConcept> externalConcepts);

	public ExternalConcept getExternalConcept(String externalIdentifier) {
		return externalConceptMap.get(externalIdentifier);
	}
	
	public void addMissingMapping(String partNum, String externalIdentifier) {
		Part part = partMap.get(partNum);
		if (part == null) {
			//Is this a compound key?
			if (partNum.contains(",")) {
				part = constructTypeAndNameForCompoundPartNum(partNum);
			} else {
				part = new Part(partNum, "Unknown Type", "Unknown to part input file.");
			}
		}
		missingPartMappings.computeIfAbsent(part, key -> new HashSet<>())
							.add(getExternalConcept(externalIdentifier));
	}

	public void removeMissingMapping(Part part) {
		missingPartMappings.remove(part);
	}

	private Part constructTypeAndNameForCompoundPartNum(String compoundPartNum) {

		String[] partNums = compoundPartNum.split(",");
		StringBuilder types = new StringBuilder();
		StringBuilder names = new StringBuilder();

		for (int i = 0; i < partNums.length; i++) {
			String partNum = partNums[i].trim();
			Part part = partMap.get(partNum);
			types.append(part.getPartTypeName() == null ? "?" : part.getPartTypeName());
			names.append(part.getPartName() == null ? "Unknown to part input file" : part.getPartName());
			if (i < partNums.length - 1) {
				types.append(" + ");
				names.append(" + ");
			}
		}

		return new Part(compoundPartNum, types.toString(), names.toString());
	}

	public AttributePartMapManager getAttributePartManager() {
		return attributePartMapManager;
	}
	
	protected void doProposedModelComparison(TemplatedConcept tc) throws TermServerScriptException {
		Concept proposedConcept = tc.getConcept();
		Concept existingConcept = tc.getExistingConcept();
		ExternalConcept externalConcept = tc.getExternalConcept();

		String previousSCG = existingConcept == null ? "N/A" : existingConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		String proposedSCG = proposedConcept == null ? "N/A" : proposedConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		String proposedDescriptionsStr = proposedConcept == null ? "N/A" : SnomedUtils.getDescriptionsToString(proposedConcept);
		
		//We might have inactivated descriptions in the existing concept if they've been changed, so
		String previousDescriptionsStr = existingConcept == null ? "N/A" : SnomedUtils.getDescriptionsToString(existingConcept, true);
		String existingConceptId = existingConcept == null ? "N/A" : existingConcept.getId();
		report(getTab(TAB_PROPOSED_MODEL_COMPARISON),
				tc.getExternalIdentifier(),
				tc.getReasonsForInterest(),
				proposedConcept != null ? proposedConcept.getId() : existingConceptId,
				tc.getIterationIndicator(),
				tc.getClass().getSimpleName(),
				tc.getDifferencesFromExistingConceptWithMultiples(),
				proposedDescriptionsStr,
				previousDescriptionsStr,
				proposedSCG, 
				previousSCG,
				externalConcept.getCommonColumns());
	}

	public abstract TemplatedConcept getAppropriateTemplate(ExternalConcept externalConcept)
			throws TermServerScriptException ;
	
	protected boolean conceptSufficientlyModeled(String contentType, String externalIdentifier, TemplatedConcept templatedConcept) throws TermServerScriptException {
		if (templatedConcept != null
				&& !(templatedConcept instanceof TemplatedConceptNull)
				&& !templatedConcept.getConcept().hasIssue(FSN_FAILURE)
				&& !templatedConcept.hasProcessingFlag(ProcessingFlag.DROP_OUT)) {
			if (templatedConcept.getConcept().getRelationships().isEmpty()) {
				throw new TermServerScriptException("Missing relationships for concept " + contentType + " " + externalIdentifier);
			}

			incrementSummaryCount(ContentPipelineManager.CONTENT_COUNT, "Content added - " + contentType);
			return true;
		}

		incrementSummaryCount(ContentPipelineManager.CONTENT_COUNT, "Content not added - " + contentType);
		if (!externalConceptMap.containsKey(externalIdentifier)) {
			incrementSummaryCount("Missing External Identifier","Identifier not found in source file - " + externalIdentifier);
		} else if (externalConceptMap.get(externalIdentifier).isHighUsage() && templatedConcept != null) {
			//Templates that come back as null will already have been counted as out of scope
			incrementSummaryCount(ContentPipelineManager.HIGH_USAGE_COUNTS,"High Usage Mapping Failure");
			if (externalConceptMap.get(externalIdentifier).isHighestUsage()) {
				incrementSummaryCount(ContentPipelineManager.HIGHEST_USAGE_COUNTS,"Highest Usage Mapping Failure");
			}
		}
		return false;
	}

	protected void validateTemplatedConcept(TemplatedConcept tc) throws TermServerScriptException {
		String externalIdentifier = tc.getExternalIdentifier();
		if (tc.getConcept() == null) {
			if (externalConceptMap.get(externalIdentifier) == null) {
				report(getTab(TAB_MODELING_ISSUES),
						externalIdentifier,
						tc.getReasonsForInterest(),
						"N/A",
						tc.getClass().getSimpleName(),
						"Critical: External identifier not found in external concept map");
			} else {
				report(getTab(TAB_MODELING_ISSUES),
						externalIdentifier,
						tc.getReasonsForInterest(),
						externalConceptMap.get(externalIdentifier).getLongDisplayName(),
						tc.getClass().getSimpleName(),
						"Concept not created");
			}
			return;
		}

		ExternalConcept externalConcept = tc.getExternalConcept();
		Concept concept = tc.getConcept();

		if (tc instanceof TemplatedConceptNull) {
			report(getTab(TAB_MODELING_ISSUES),
					externalConcept.getExternalIdentifier(),
					tc.getReasonsForInterest(),
					externalConcept.getLongDisplayName(),
					"Does not meet criteria for template match",
					tc.getClass().getSimpleName(),
					"Property: " + externalConcept.getProperty());
		} else if (!tc.hasProcessingFlag(ProcessingFlag.DROP_OUT)) {
			String fsn = concept.getFsn();
			boolean insufficientTermPopulation = fsn.contains("[");
			//Some panels have words like '[Moles/volume]' in them, so check also for slot token names (all caps).  Not Great.
			if (insufficientTermPopulation && hasAllCapsSlot(fsn)) {
				concept.addIssue(FSN_FAILURE + " to populate required slot: " + fsn);
				tc.addProcessingFlag(ProcessingFlag.DROP_OUT);
			}
		}

		if (tc.hasProcessingFlag(ProcessingFlag.DROP_OUT) && !concept.hasIssues()) {
			LOGGER.warn("Concept with drop out flag does not list any problems {}", tc);
		}

		if (concept.hasIssues() ) {
			report(getTab(TAB_MODELING_ISSUES),
					externalConcept.getExternalIdentifier(),
					tc.getReasonsForInterest(),
					externalConcept.getLongDisplayName(),
					tc.getClass().getSimpleName(),
					tc.getConcept().getIssues(",\n"));
		}
		flushFilesSoft();
	}
	
	/**
	 * Checks if a string contains tokens enclosed in square brackets that are all in capital letters.
	 *
	 * @param fsn The string to check.
	 * @return true if there is at least one token in all caps within square brackets; false otherwise.
	 */
	private boolean hasAllCapsSlot(String fsn) {
		Matcher matcher = allCapsSlotPattern.matcher(fsn);
		return matcher.find();
	}
	
	protected boolean containsObjectionableWord(ExternalConcept externalConcept) throws TermServerScriptException {
		//Does this LoincNum feature an objectionable word?  Skip if so.
		for (String objectionableWord : getObjectionableWords()) {
			if (externalConcept.getLongDisplayName() == null) {
				LOGGER.debug("Unable to obtain display name for {}", externalConcept.getExternalIdentifier());
			} else if (normaliseLCN(externalConcept).contains(" " + objectionableWord + " ")) {
				report(getTab(TAB_MODELING_ISSUES),
						externalConcept.getExternalIdentifier(),
						"",
						externalConcept.getLongDisplayName(),
						"",
						"Contains objectionable word - " + objectionableWord);
				return true;
			}
		}
		return false;
	}

	private String normaliseLCN(ExternalConcept externalConcept) {
		return " " + externalConcept.getLongDisplayName().toLowerCase() + " ";
	}

	protected boolean confirmExternalIdentifierExists(String externalIdentifier) throws TermServerScriptException {
		//Do we have consistency between the detail map and the main loincTermMap?
		if (!externalConceptMap.containsKey(externalIdentifier)) {
			report(getTab(TAB_MODELING_ISSUES),
					externalIdentifier,
					"",
					"N/A",
					"N/A",
					"Failed integrity. Identifier " + externalIdentifier + " from detail file, not known in main external concept file.");
			return false;
		}
		return true;
	}
	
	
	protected String inScope(String property) throws TermServerScriptException {
		//Construct a dummy LoincNum with this property and see if it's in scope or not
		ExternalConceptNull dummy = new ExternalConceptNull(DUMMY_EXTERNAL_IDENTIFIER, property);
		return getAppropriateTemplate(dummy) instanceof TemplatedConceptNull ? "N" : "Y";
	}

	public String getPrimaryLangRefset() {
		return primaryLangRefset;
	}

	protected void setPrimaryLangRefset(String primaryLangRefset) {
		this.primaryLangRefset = primaryLangRefset;
	}

}