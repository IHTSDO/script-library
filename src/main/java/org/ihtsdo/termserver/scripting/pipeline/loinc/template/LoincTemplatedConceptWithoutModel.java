package org.ihtsdo.termserver.scripting.pipeline.loinc.template;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincTerm;
import org.ihtsdo.termserver.scripting.util.CaseSensitivityUtils;

import java.util.ArrayList;
import java.util.List;

public class LoincTemplatedConceptWithoutModel extends LoincTemplatedConcept {

	protected LoincTemplatedConceptWithoutModel(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	public static LoincTemplatedConceptWithoutModel create(ExternalConcept externalConcept) throws TermServerScriptException {
		LoincTemplatedConceptWithoutModel templatedConcept = new LoincTemplatedConceptWithoutModel(externalConcept);
		templatedConcept.createConcept();
		return templatedConcept;
	}

	protected void createConcept() throws TermServerScriptException {
		concept = Concept.withDefaults(null);
		concept.setModuleId(SCTID_LOINC_EXTENSION_MODULE);
		concept.setDefinitionStatus(DefinitionStatus.PRIMITIVE);
	}

	@Override
	protected void populateTerms() throws TermServerScriptException {
		LoincTerm loincTerm = getLoincTerm();
		String ptStr = loincTerm.getLongCommonName();

		Description pt = Description.withDefaults(ptStr, DescriptionType.SYNONYM, Acceptability.PREFERRED);
		pt.addIssue(CaseSensitivityUtils.FORCE_CS);

		Description fsn = Description.withDefaults(ptStr + getSemTag(), DescriptionType.FSN, Acceptability.PREFERRED);
		fsn.addIssue(CaseSensitivityUtils.FORCE_CS);

		//Add in the traditional colon form that we've previously used as the FSN
		Description colonDesc = Description.withDefaults(loincTerm.getColonizedTerm(), DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
		colonDesc.addIssue(CaseSensitivityUtils.FORCE_CS);

		concept.addDescription(pt);
		concept.addDescription(fsn);
		concept.addDescription(colonDesc);
	}

	@Override
	protected List<RelationshipTemplate> determineComponentAttributes(boolean expectNullMap) {
		//Following the rules detailed in https://docs.google.com/document/d/1rz2s3ga2dpdwI1WVfcQMuRXWi5RgpJOIdicgOz16Yzg/edit
		//With respect to the values read from Loinc_Detail_Type_1 file

		//Concepts without models have no attributes
		return new ArrayList<>();
	}

}
