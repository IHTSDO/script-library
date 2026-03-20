package org.ihtsdo.termserver.scripting.pipeline;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
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

	private static final String DESCRIPTIONS = "Descriptions";

	private static final String DELTA_NEW = "Delta New";
	private static final String DELTA_CHANGED = "Delta Changed";
	private static final String DELTA_NEW_CHANGED = "Delta New/Changed";
	private static final String DELTA_INACTIVATED = "Delta Inactivated";

	private static final String ON_INTERNATIONAL_CONCEPT = " on International Concept";

	private List<Concept> inScopeConcepts;

	enum Mode { PUBLISHED, UNPUBLISHED }
	Mode mode = Mode.PUBLISHED;
	private String packageEffectiveDate;
	
	boolean includeDetails = false;  //Note that born inactive components are always included
	String inScopeNamespace = null;

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> parameters = new HashMap<>();
		parameters.put(MODULES, SCTID_LOINC_EXTENSION_MODULE);
		TermServerScript.run(ProductExtensionSummary.class, args, parameters);
	}

	@Override
	protected void init (JobRun jobRun) throws TermServerScriptException {
		super.init(jobRun);
		getArchiveManager().setPopulateReleaseFlag(true);
		getArchiveManager().setLoadOtherReferenceSets(true);
		if (mode == Mode.PUBLISHED) {
			getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
			getGraphLoader().setRecordPreviousState(true);
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] tabNames = new String[] {
				"Summary Counts",
				"Concept Details",
				"Concepts without Alternate Identifiers",
				"Concepts with multiple Axioms",
				DESCRIPTIONS,
				"Text Definitions",
				"Inactive Components",
				"Born Inactive Components"
		};
		String[] columnHeadings = new String[] {
				"Category, Item, Count",
				"Concept, FSN, SemTag, Alternate Identifier, Descriptions, Inferred Model, , ",
				"Concept, FSN, SemTag",
				"Concept, FSN, SemTag",
				"SCTID, active, Term",
				"Concept, FSN, SemTag, Definition",
				"Component, EffectiveTime, Active, Module, Author",
				"ID, Component Type, Component"
		};
		postInit(tabNames, columnHeadings);
		inScopeConcepts = gl.getAllConcepts().stream()
				.filter(this::inScope)
				.sorted(SnomedUtils::compareSemTagFSN)
				.toList();
		determineNamespace();
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
		reportSummaryCounts(PRIMARY_REPORT);

		if (includeDetails) {
			getConceptDetails(SECONDARY_REPORT);
			getConceptsWithoutAltIds(TERTIARY_REPORT);
			getConceptsWithMultipleAxioms(QUATERNARY_REPORT);
			getTextDefinitions(SENARY_REPORT);
			getInactiveComponents(SEPTENARY_REPORT);
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
				incrementSummaryCount(category, c.getComponentType() + ON_INTERNATIONAL_CONCEPT);
			}
		}
	}

	private void doSnapshotCounts(Component c) throws TermServerScriptException {
		String category = SNAPSHOT + (c.isActiveSafely()? "_Active" : "_Inactive");
		String counter = (c.isActiveSafely()? "Active_" : "Inactive_") + c.getComponentType();
		incrementSummaryCount(category, counter);

		if (includeDetails && c instanceof Description d) {
			report(QUINARY_REPORT, d.getId(), d.getActive(), d.getTerm());
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
			return c.getEffectiveTime().isEmpty();
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
			report(tabIdx, c, SnomedUtils.getAlternateIdentifiers(c, false), SnomedUtils.getDescriptions(c), c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
		}
	}

	private void getConceptsWithoutAltIds(int tabIdx) throws TermServerScriptException {
		for (Concept c : inScopeConcepts) {
			if (c.getAlternateIdentifiers().isEmpty()) {
				incrementSummaryCount(SNAPSHOT_ACTIVE, "Concepts without AltIds");
				report(tabIdx, c);
			}
		}
	}

	private void getConceptsWithMultipleAxioms(int tabIdx) throws TermServerScriptException {
		// Only interested in multiple axioms where one of them is in scope.
		// Watch out that we might have an unexpected LOINC axiom on an International Concept
		List<Concept> conceptsOfInterest = gl.getAllConcepts().stream()
				.filter(c -> c.getAxiomEntries().size() > 1)
				.filter(c -> c.getAxiomEntries().stream().anyMatch(this::inScope))
				.sorted(SnomedUtils::compareSemTagFSN)
				.toList();

		for (Concept c : conceptsOfInterest) {
			String axiomStr = c.getAxiomEntries().stream()
					.map(AxiomEntry::toString)
					.collect(Collectors.joining(",\n"));
			report(tabIdx, c, axiomStr);
		}
	}

	private void getTextDefinitions(int tabIdx) throws TermServerScriptException {
		for (Concept concept : gl.getAllConcepts()) {
			List<Description> inScopeDescriptions = concept.getDescriptions(ActiveState.ACTIVE, List.of(DescriptionType.TEXT_DEFINITION)).stream()
					.filter(c -> inScope(c, true))
					.toList();
			for (Description d : inScopeDescriptions) {
				report(tabIdx, concept, d);
			}
		}
	}

	private void getInactiveComponents(int tabIdx) throws TermServerScriptException {
		for (Concept concept : gl.getAllConcepts()) {
			for (Component c : SnomedUtils.getAllComponents(concept)) {
				if (!c.isActiveSafely() && inScope(c, true)) {
					Concept parent = gl.getComponentOwner(c.getId());
					report(tabIdx, c, c.getEffectiveTime(), c.isActive(), c.getModuleId(), parent);
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
					report(OCTONARY_REPORT, c.getId(), c);
				}
			}
		}
	}
}
