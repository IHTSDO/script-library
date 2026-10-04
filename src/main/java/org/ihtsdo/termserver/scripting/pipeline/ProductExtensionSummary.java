package org.ihtsdo.termserver.scripting.pipeline;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProductExtensionSummary extends TermServerReport implements ReportClass {

	private static final String SNAPSHOT_ACTIVE = "Snapshot Active";

	private static final String DELTA_NEW = "Delta New";
	private static final String DELTA_CHANGED = "Delta Changed";
	private static final String DELTA_NEW_CHANGED = "Delta New/Changed";
	private static final String DELTA_INACTIVATED = "Delta Inactivated";

	private static final String TO_INTERNATIONAL_CONCEPT = " to International Concept";

	private static final String CONCEPT_FSN_SEMTAG = "Concept, FSN, SemTag";

	private enum Tab {
		SUMMARY_COUNTS("Summary Counts", "Category, Item, Count", false),
		CONCEPT_DETAILS("Concept Details", "Concept, FSN, SemTag, Alternate Identifier, Descriptions, Inferred Model, Promoted", true),
		CONCEPTS_WITHOUT_ALT_IDS("Concepts without Alternate Identifiers", CONCEPT_FSN_SEMTAG + ", Promoted", true),
		CONCEPTS_WITH_MULTIPLE_AXIOMS("Concepts with multiple Axioms", CONCEPT_FSN_SEMTAG + ", Axioms, Promoted", true),
		DESCRIPTIONS("Descriptions", "SCTID, active, Term", true),
		TEXT_DEFINITIONS("Text Definitions", "Concept, FSN, SemTag, Definition, Promoted", true),
		INACTIVE_COMPONENTS("Inactive Components", "Component, EffectiveTime, Active, Module, Concept, Promoted", true),
		BORN_INACTIVE_COMPONENTS("Born Inactive Components", "ID, Component Type, Component", false),
		PROMOTED_CONCEPTS("Promoted Concepts", CONCEPT_FSN_SEMTAG, false),
		CORE_COMPONENTS_IN_DELTA("Core Components in Delta", "Component Type, Module, Component, Concept", false);

		private final String tabName;
		private final String columnHeadings;
		private final boolean detailOnly;

		Tab(String tabName, String columnHeadings, boolean detailOnly) {
			this.tabName = tabName;
			this.columnHeadings = columnHeadings;
			this.detailOnly = detailOnly;
		}
	}

	private final Map<Tab, Integer> tabIndexes = new EnumMap<>(Tab.class);

	private List<Concept> inScopeConcepts;

	enum Mode { PUBLISHED, UNPUBLISHED }
	Mode mode = Mode.UNPUBLISHED;
	private String packageEffectiveDate;
	
	boolean includeDetails = true;  //Note that born inactive components are always included
	String inScopeNamespace = null;

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> parameters = new HashMap<>();
		parameters.put(MODULES, SCTID_LOINC_EXTENSION_MODULE);
		TermServerScript.run(ProductExtensionSummary.class, args, parameters);
	}

	@Override
	protected void init (JobRun jobRun) throws TermServerScriptException {
		super.init(jobRun);
		getSnapshotConfiguration().setPopulateReleaseFlag(true);
		getSnapshotConfiguration().setLoadOtherReferenceSets(true);
		if (mode == Mode.PUBLISHED) {
			getGraphLoader().setRecordPreviousState(true);
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		List<Tab> tabsInUse = Arrays.stream(Tab.values())
				.filter(t -> includeDetails || !t.detailOnly)
				.toList();
		for (int i = 0; i < tabsInUse.size(); i++) {
			tabIndexes.put(tabsInUse.get(i), i);
		}
		postInit(tabsInUse.stream().map(t -> t.tabName).toArray(String[]::new),
				tabsInUse.stream().map(t -> t.columnHeadings).toArray(String[]::new));
		//Namespace must be known before we can tell which concepts originated in the product
		determineNamespace();
		inScopeConcepts = gl.getAllConcepts().stream()
				.filter(this::originatedInProduct)
				.sorted(SnomedUtils::compareSemTagFSN)
				.toList();
	}

	private int getTab(Tab tab) throws TermServerScriptException {
		Integer tabIdx = tabIndexes.get(tab);
		if (tabIdx == null) {
			throw new TermServerScriptException("Tab '" + tab.tabName + "' is only available when details are included");
		}
		return tabIdx;
	}

	/**
	 * A component belongs to the product if it's in one of the product's modules, or if its SCTID is in the
	 * product's namespace - which catches concepts promoted to the International Edition (now in the core module).
	 * Use for content checks. Anything that depends on what the release build extracts must use inScope(),
	 * which is module-only.
	 */
	private boolean originatedInProduct(Component c) {
		return inScope(c) || (inScopeNamespace != null
				&& SnomedUtilsBase.isSctid(c.getId())
				&& inScopeNamespace.equals(SnomedUtilsBase.getNamespace(c.getId())));
	}

	private String promoted(Concept c) {
		return c != null && !inScope(c) && originatedInProduct(c) ? "Y" : "";
	}

	private void determineNamespace() {
		if (mode == Mode.PUBLISHED) {
			obtainPackageMetadata();
		} else {
			Set<String> inScopeNamespaces = getInScopeNamespaces();
			if (inScopeNamespaces.size() != 1) {
				throw new IllegalArgumentException("Expected only one namespace, but got " + inScopeNamespaces.size() + " namespaces");
			}
			inScopeNamespace = inScopeNamespaces.iterator().next();
		}
	}

	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Product Extension Summary")
				.withDescription("This report list summary counts for a particular product extension, with cross checks.")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withParameters(new JobParameters())
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		getSummaryCounts();
		checkForBornInactiveComponents();
		checkForCoreComponentsInDelta(getTab(Tab.CORE_COMPONENTS_IN_DELTA));
		checkForConceptsWithoutAltIds();
		reportSummaryCounts(getTab(Tab.SUMMARY_COUNTS));

		if (includeDetails) {
			getConceptDetails(getTab(Tab.CONCEPT_DETAILS));
			getConceptsWithMultipleAxioms(getTab(Tab.CONCEPTS_WITH_MULTIPLE_AXIOMS));
			getTextDefinitions(getTab(Tab.TEXT_DEFINITIONS));
			getInactiveComponents(getTab(Tab.INACTIVE_COMPONENTS));
		}
	}

	private void getSummaryCounts() throws TermServerScriptException {
		//Some components belong to International Concepts, so start with the full set, then consider
		//scope at the component level
		for (Concept c : gl.getAllConcepts()) {
			getSummaryCounts(c);
		}
	}

	private void getSummaryCounts(Concept concept) throws TermServerScriptException {
		for (Component c : SnomedUtils.getAllComponents(concept)) {
			if (inScope(c)) {
				doSnapshotCounts(c);
				doDeltaCounts(c);
			} else if (!inScope(concept) && SnomedUtilsBase.isSctid(c.getId()) && SnomedUtilsBase.getNamespace(c.getId()).equals(inScopeNamespace)) {
				String category = c.isActiveSafely() ? "Snapshot Promoted Active" : "Snapshot Promoted Inactive";
				incrementSummaryCount(category, c.getComponentType() + TO_INTERNATIONAL_CONCEPT);
				if (c.getComponentType().equals(Component.ComponentType.CONCEPT)) {
					report(getTab(Tab.PROMOTED_CONCEPTS), concept);
				}
			}
		}
	}

	private void doSnapshotCounts(Component c) throws TermServerScriptException {
		String category = SNAPSHOT + (c.isActiveSafely()? "_Active" : "_Inactive");
		String counter = (c.isActiveSafely()? "Active_" : "Inactive_") + c.getComponentType();
		incrementSummaryCount(category, counter);

		if (includeDetails && c instanceof Description d) {
			report(getTab(Tab.DESCRIPTIONS), d.getId(), d.getActive(), d.getTerm());
		}
	}

	private void doDeltaCounts(Component c) {
		if (isInDelta(c)) {
			String category = determineDeltaCategory(c);
			incrementSummaryCount(category, c.getComponentType().toString());
		}
	}

	private String determineDeltaCategory(Component c) {
		//We know it's a delta, so is it new, changed or inactivated?
		if (!c.isActiveSafely()) {
			return DELTA_INACTIVATED;
		} else if (mode == Mode.PUBLISHED) {
			return DELTA_NEW_CHANGED; //Can't tell the difference between new and changed with only a snapshot import
		} else {
			return c.isReleasedSafely() ? DELTA_CHANGED : DELTA_NEW;
		}
	}

	private boolean isInDelta(Component c) {
		if (mode == Mode.PUBLISHED) {
			return c.getEffectiveTime().equals(packageEffectiveDate);
		} else {
			return StringUtils.isEmpty(c.getEffectiveTime());
		}
	}

	private void obtainPackageMetadata() {
		// Regex: capture the last 7 digits before the last underscore, then 8-digit date
		Pattern pattern = Pattern.compile(".*?(\\d{7})_(\\d{8})T\\d{6}Z\\.zip$");
		Matcher matcher = pattern.matcher(projectName);
		if (matcher.find()) {
			inScopeNamespace = matcher.group(1);       // 1010000
			packageEffectiveDate = matcher.group(2);   // 20260321
		} else {
			throw new IllegalArgumentException(
					"Filename does not match expected pattern to extract namespace and date: " + projectName
			);
		}
	}

	private void getConceptDetails(int tabIdx) throws TermServerScriptException {
		for (Concept c : inScopeConcepts) {
			report(tabIdx, c, SnomedUtils.getAlternateIdentifiers(c, false), SnomedUtils.getDescriptions(c), c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP), promoted(c));
		}
	}

	private void checkForConceptsWithoutAltIds() throws TermServerScriptException {
		//Counted whether or not details are included, so must run before the summary counts are reported
		for (Concept c : inScopeConcepts) {
			if (c.getAlternateIdentifiers().isEmpty()) {
				incrementSummaryCount(SNAPSHOT_ACTIVE, "Concepts without AltIds");
				if (includeDetails) {
					report(getTab(Tab.CONCEPTS_WITHOUT_ALT_IDS), c, promoted(c));
				}
			}
		}
	}

	private void getConceptsWithMultipleAxioms(int tabIdx) throws TermServerScriptException {
		// Only interested in multiple axioms where one of them is in scope.
		// Watch out that we might have an unexpected LOINC axiom on an International Concept
		List<Concept> conceptsOfInterest = gl.getAllConcepts().stream()
				.filter(c -> c.getAxiomEntries().size() > 1)
				.filter(c -> c.getAxiomEntries().stream().anyMatch(this::originatedInProduct))
				.sorted(SnomedUtils::compareSemTagFSN)
				.toList();

		for (Concept c : conceptsOfInterest) {
			String axiomStr = c.getAxiomEntries().stream()
					.map(AxiomEntry::toString)
					.collect(Collectors.joining(",\n"));
			report(tabIdx, c, axiomStr, promoted(c));
		}
	}

	private void getTextDefinitions(int tabIdx) throws TermServerScriptException {
		for (Concept concept : gl.getAllConcepts()) {
			List<Description> inScopeDescriptions = concept.getDescriptions(ActiveState.ACTIVE, List.of(DescriptionType.TEXT_DEFINITION)).stream()
					.filter(this::originatedInProduct)
					.toList();
			for (Description d : inScopeDescriptions) {
				report(tabIdx, concept, d, promoted(concept));
			}
		}
	}

	private void getInactiveComponents(int tabIdx) throws TermServerScriptException {
		for (Concept concept : gl.getAllConcepts()) {
			for (Component c : SnomedUtils.getAllComponents(concept)) {
				if (!c.isActiveSafely() && originatedInProduct(c)) {
					Concept parent = gl.getComponentOwner(c.getId());
					report(tabIdx, c, c.getEffectiveTime(), c.isActive(), c.getModuleId(), parent, promoted(parent));
				}
			}
		}
	}

	private void checkForCoreComponentsInDelta(int tabIdx) throws TermServerScriptException {
		//The release build only extracts rows from the expected modules, so any component changed
		//in the delta but left in another module (e.g. an International description inactivated
		//without its module being changed) silently goes missing from the release.
		//Check all concepts, not just those in scope, as these changes are typically on International concepts
		for (Concept concept : gl.getAllConcepts()) {
			for (Component c : SnomedUtils.getAllComponents(concept)) {
				if (isInDelta(c) && !inScope(c)) {
					incrementSummaryCount("Delta", "Core Components in Delta");
					Concept owner = gl.getComponentOwner(c.getId());
					report(tabIdx, c.getComponentType(), c.getModuleId(), c.toString(), owner);
				}
			}
		}
	}

	private void checkForBornInactiveComponents() throws TermServerScriptException {
		//We're going to check _all_ concepts to ensure that components in the product's module
		//that might appear on International concepts are included
		for (Concept concept : gl.getAllConcepts()) {
			for (Component c : SnomedUtils.getAllComponents(concept)) {
				if (inScope(c) && !c.isActiveSafely() && !c.isReleasedSafely()) {
					incrementSummaryCount(SNAPSHOT_ACTIVE, "Born Inactive Components");
					report(getTab(Tab.BORN_INACTIVE_COMPONENTS), c.getId(), c);
				}
			}
		}
	}
}
