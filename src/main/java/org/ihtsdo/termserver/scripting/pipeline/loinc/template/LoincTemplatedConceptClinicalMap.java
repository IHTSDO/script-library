package org.ihtsdo.termserver.scripting.pipeline.loinc.template;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.pipeline.domain.Part;
import org.ihtsdo.termserver.scripting.pipeline.loinc.LoincScriptConstants;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincTerm;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConcept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import java.util.List;

/**
 * A clinical LOINC term mapped to an existing SNOMED CT concept.  Rather than modelling a new concept,
 * we augment a clone of the existing one with the LOINC alternate identifier and LOINC descriptions.
 */
public class LoincTemplatedConceptClinicalMap extends TemplatedConcept implements LoincScriptConstants {

	private static final String NOT_MODELLED = "Clinical LOINC terms are mapped, not modelled";

	private LoincTemplatedConceptClinicalMap(LoincTerm loincTerm) {
		super(loincTerm);
	}

	public static LoincTemplatedConceptClinicalMap create(LoincTerm loincTerm, Concept target) {
		LoincTemplatedConceptClinicalMap tc = new LoincTemplatedConceptClinicalMap(loincTerm);
		tc.setExistingConcept(target);
		Concept clone = target.cloneWithIds();
		//Only what we add should be output
		SnomedUtils.getAllComponents(clone).forEach(Component::setClean);
		tc.setConcept(clone);
		return tc;
	}

	public LoincTerm getLoincTerm() {
		return (LoincTerm) externalConcept;
	}

	/**
	 * @return true if the existing concept already carries this LOINC code
	 */
	public boolean addAlternateIdentifier(String moduleId) {
		Concept existing = getExistingConcept();
		if (existing.hasAlternateIdentifier(getSchemaId())
				&& existing.getAlternateIdentifierForScheme(getSchemaId()).getAlternateIdentifier().equals(getExternalIdentifier())) {
			concept.addAlternateIdentifier(existing.getAlternateIdentifierForScheme(getSchemaId()));
			return true;
		}
		concept.addAlternateIdentifier(getExternalIdentifier(), getSchemaId());
		concept.getAlternateIdentifierForScheme(getSchemaId()).setModuleId(moduleId);
		return false;
	}

	/**
	 * @return the number of descriptions added
	 */
	public int addLoincDescriptions(String moduleId) throws TermServerScriptException {
		int added = 0;
		for (String term : List.of(getLoincTerm().getLongCommonName(), getLoincTerm().getColonizedTerm())) {
			if (concept.getDescriptions(ActiveState.ACTIVE).stream().noneMatch(d -> d.getTerm().equals(term))) {
				Description d = Description.withDefaults(term, DescriptionType.SYNONYM, defaultAccAcceptabilityMap);
				d.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
				d.setModuleId(moduleId);
				d.getLangRefsetEntries().forEach(l -> l.setModuleId(moduleId));
				concept.addDescription(d);
				added++;
			}
		}
		return added;
	}

	@Override
	public String getSchemaId() {
		return SCTID_LOINC_SCHEMA;
	}

	@Override
	protected String getSemTag() {
		throw new IllegalStateException(NOT_MODELLED);
	}

	@Override
	protected void applyTemplateSpecificModellingRules(List<RelationshipTemplate> attributes, Part part, RelationshipTemplate rt) {
		throw new IllegalStateException(NOT_MODELLED);
	}

	@Override
	protected void applyTemplateSpecificTermingRules(Description pt) {
		throw new IllegalStateException(NOT_MODELLED);
	}

	@Override
	protected void populateParts() {
		throw new IllegalStateException(NOT_MODELLED);
	}

	@Override
	protected boolean detailsIndicatePrimitiveConcept() {
		throw new IllegalStateException(NOT_MODELLED);
	}
}
