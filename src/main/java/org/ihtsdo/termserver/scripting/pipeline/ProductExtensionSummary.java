package org.ihtsdo.termserver.scripting.pipeline;

import org.ihtsdo.otf.exception.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
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
import java.util.stream.Collectors;

public class ProductExtensionSummary extends TermServerReport implements ReportClass {

	private static final String ACTIVE_CONCEPTS = "Active Concepts";
	private static final String ACTIVE_PROMOTED_CONCEPT = "Active Promoted Concepts";
	private static final String ACTIVE_DESCRIPTIONS = "Active Descriptions";
	private static final String ACTIVE_PROMOTED_DESCRIPTIONS = "Active Promoted Descriptions";
	private static final String ACTIVE_AXIOMS = "Active Axioms";
	private static final String ACTIVE_RELATIONSHIPS = "Active Relationships";
	private static final String ACTIVE_SIMPLE_REFSET_MEMBERS = "Active Simple Refset Members";
	private static final String ACTIVE_ALT_IDS = "Active Alternate Identifiers";

	private static final String INACTIVE_CONCEPTS = "Inactive Concepts";
	private static final String INACTIVE_PROMOTED_CONCEPT = "Inactive Promoted Concepts";
	private static final String INACTIVE_DESCRIPTIONS = "Inactive Descriptions";
	private static final String INACTIVE_PROMOTED_DESCRIPTIONS = "Inactive Promoted Descriptions";
	private static final String INACTIVE_AXIOMS = "Inactive Axioms";
	private static final String INACTIVE_RELATIONSHIPS = "Inactive Relationships";
	private static final String INACTIVE_SIMPLE_REFSET_MEMBERS = "Inactive Simple Refset Members";
	private static final String INACTIVE_ALT_IDS = "Inactive Alternate Identifiers";

	private static final String DESCRIPTION_ON_INTERNATIONAL_CONCEPT = "Description on International Concept";

	private List<Concept> inScopeConcepts;

	enum Mode { PUBLISHED, UNPUBLISHED }
	Mode mode = Mode.UNPUBLISHED;
	boolean includeDetails = true;  //Note that born inactive components are always included
	String inScopeNamespace = null;

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> parameters = new HashMap<>();
		parameters.put(MODULES, SCTID_LOINC_EXTENSION_MODULE);
		TermServerScript.run(ProductExtensionSummary.class, args, parameters);
	}

	@Override
	protected void init (JobRun jobRun) throws TermServerScriptException {
		getArchiveManager().setPopulateReleaseFlag(true);
		getArchiveManager().setLoadOtherReferenceSets(true);
		super.init(jobRun);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] tabNames = new String[] {
				"Summary Counts",
				"Concept Details",
				"Concepts without Alternate Identifiers",
				"Concepts with multiple Axioms",
				"Descriptions",
				"Text Definitions",
				"Inactive Components",
				"Born Inactive Components"
		};
		String[] columnHeadings = new String[] {
				"Item, Count",
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
		Set<String> inScopeNamespaces = getInScopeNamespaces();
		if (inScopeNamespaces.size() != 1) {
			throw new IllegalArgumentException("Expected only one namespace, but got " + inScopeNamespaces.size() + " namespaces");
		}
		inScopeNamespace = inScopeNamespaces.iterator().next();
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
		populateSummaryTabAndTotal(PRIMARY_REPORT);

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
			if (c.getId().equals("703151010000100")) {
				System.out.println("Not counted");
			}
			getSummaryCounts(c);
		}
	}

	private void getSummaryCounts(Concept c) throws TermServerScriptException {
		getConceptSummaryCounts(c);
		getDescriptionSummaryCounts(c);
		getAxiomSummaryCounts(c);
		getRelationshipSummaryCounts(c);
		getOtherRefsetSummaryCounts(c);
		getAlternateIdentifierSummaryCounts(c);
	}

	private void getAxiomSummaryCounts(Concept c) {
		for (AxiomEntry ax : c.getAxiomEntries()) {
			if (inScope(ax)) {
				incrementSummaryInformation(ax.isActiveSafely() ? ACTIVE_AXIOMS : INACTIVE_AXIOMS);
			}
		}
	}

	private void getDescriptionSummaryCounts(Concept c) throws TermServerScriptException {
		for (Description d : c.getDescriptions(ActiveState.BOTH)) {
			if (inScope(d)) {
				incrementSummaryInformation(d.isActiveSafely() ? ACTIVE_DESCRIPTIONS : INACTIVE_DESCRIPTIONS);
				//Is this a change on an International concept?
				if (!inScope(c)) {
					incrementSummaryInformation(DESCRIPTION_ON_INTERNATIONAL_CONCEPT);
				}
				if (includeDetails) {
					report(QUINARY_REPORT, d.getId(), d.getActive(), d.getTerm());
				}
			} else if (SnomedUtilsBase.getNamespace(d.getId()).equals(inScopeNamespace)) {
				incrementSummaryInformation(d.isActiveSafely() ? ACTIVE_PROMOTED_DESCRIPTIONS : INACTIVE_PROMOTED_DESCRIPTIONS);
			}
		}
	}

	private void getAlternateIdentifierSummaryCounts(Concept c) {
		for (AlternateIdentifier ai : c.getAlternateIdentifiers()) {
			incrementSummaryInformation(ai.isActiveSafely() ? ACTIVE_ALT_IDS : INACTIVE_ALT_IDS);
			boolean isNew = determineIfNew(ai);
			if (ai.isActiveSafely() && (isNew || determineIfChanged(ai))) {
				incrementSummaryInformation( "New/Changed Alternate Identifier");
			}
		}
	}

	private void getOtherRefsetSummaryCounts(Concept c) {
		for (RefsetMember rm : c.getOtherRefsetMembers()) {
			incrementSummaryInformation(rm.isActiveSafely() ? ACTIVE_SIMPLE_REFSET_MEMBERS : INACTIVE_SIMPLE_REFSET_MEMBERS);
			boolean isNew = determineIfNew(rm);
			if (rm.isActiveSafely() && (isNew || determineIfChanged(rm))) {
				incrementSummaryInformation( "New/Changed Simple Refset Member");
			}
		}
	}

	private void getConceptSummaryCounts(Concept c) throws TermServerScriptException {
		if (inScope(c)) {
			incrementSummaryInformation(c.isActiveSafely() ? ACTIVE_CONCEPTS : INACTIVE_CONCEPTS);
			boolean isNew = determineIfNew(c);
			if (isNew) {
				boolean isObservable = c.getAncestors(NOT_SET).contains(OBSERVABLE_ENTITY);
				incrementSummaryInformation(isObservable ? "New Observable Entities" : "New Non-Observable Entities");
			} else if (determineIfChanged(c)) {
				incrementSummaryInformation(c.isActiveSafely() ? "Changed Concept Row" : "Inactivated Concept Row");
			}
		} else if (SnomedUtilsBase.getNamespace(c.getId()).equals(inScopeNamespace)) {
			incrementSummaryInformation(c.isActiveSafely() ? ACTIVE_PROMOTED_CONCEPT : INACTIVE_PROMOTED_CONCEPT);
		}
	}

	private void getRelationshipSummaryCounts(Concept c) {
		//No need to look at stated relationships, they're covered by axiom counts
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.BOTH)) {
			if (inScope(r, true)) {
				incrementSummaryInformation(r.isActiveSafely() ? ACTIVE_RELATIONSHIPS : INACTIVE_RELATIONSHIPS);
				if (r.getCharacteristicType().equals(CharacteristicType.INFERRED_RELATIONSHIP)) {
					boolean relIsNew = determineIfNew(r);
					if (relIsNew && !r.isActiveSafely()) {
						incrementSummaryInformation("New but inactive inferred relationship");
					}
				}
			}
		}
	}

	private boolean determineIfChanged(Component c) {
		if (mode == Mode.UNPUBLISHED) {
			// We're only interested in unpublished components
			return StringUtils.isEmpty(c.getEffectiveTime());
		} else {
			throw new NotImplementedException();
		}
	}

	private boolean determineIfNew(Component c) {
		if (mode == Mode.UNPUBLISHED) {
			return !c.isReleased();
		} else {
			throw new NotImplementedException();
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
				incrementSummaryInformation("Concepts without AltIds");
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
					incrementSummaryInformation("Born Inactive Components");
					report(OCTONARY_REPORT, c.getId(), c);
				}
			}
		}
	}
}
