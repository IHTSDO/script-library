package org.ihtsdo.termserver.scripting.pipeline.loinc.template;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincDetail;

import java.util.ArrayList;
import java.util.List;

import static org.ihtsdo.termserver.scripting.pipeline.ContentPipeLineConstants.ProcessingFlag.ALTERNATIVE_COMPONENT_SUPPLIED;

public class LoincTemplatedConceptWithInheresAndInherent extends LoincTemplatedConceptWithInheres{

	private static final String MUTATIONS_TESTED_FOR_PARTNUM = "LP32421-7";

	protected LoincTemplatedConceptWithInheresAndInherent(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	public static LoincTemplatedConcept create(ExternalConcept externalConcept) throws TermServerScriptException {
		LoincTemplatedConceptWithInheresAndInherent templatedConcept = new LoincTemplatedConceptWithInheresAndInherent(externalConcept);
		templatedConcept.populateTypeMapCommonItems();
		templatedConcept.typeMap.put(LOINC_PART_TYPE_COMPONENT, gl.getConcept("704319004 |Inheres in (attribute)|"));
		templatedConcept.setTermTemplate("[PROPERTY] of [COMPONENT] in [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [CHALLENGE]");
		return templatedConcept;
	}

	protected List<RelationshipTemplate> determineComponentAttributes(boolean expectNullMap) throws TermServerScriptException {

		List<RelationshipTemplate> attributes = new ArrayList<>();

		if (detailPresent(COMPONENTCORE_PN)) {
			LoincDetail coreDetail = getLoincDetailOrThrow(COMPONENTCORE_PN);
			addReasonForInterest("LE-160");
			attributes.addAll(getAdditionalAttributes(coreDetail, INHERENT_LOCATION));
			//This is being used in the component slot, so tip off the FSN generation that we're
			//looking for a different attribute type
			typeMap.put(LOINC_PART_TYPE_COMPONENT2, ScriptConstants.INHERENT_LOCATION);

			addProcessingFlag(ALTERNATIVE_COMPONENT_SUPPLIED);
			setTermTemplate("[PROPERTY] of [COMPONENT] in [COMPONENT2] in [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [CHALLENGE]");
		}

		if (detailPresent(COMPNUMSUFFIX_PN)) {
			LoincDetail compSuffix = getLoincDetailOrThrow(COMPNUMSUFFIX_PN);
			attributes.addAll(getAdditionalAttributes(compSuffix, INHERES_IN));

			//See specific terming instruction related to MUTATIONS_TESTED_FOR_PARTNUM - no need to repeat ourselves
			if (compSuffix.getPartNumber().equals(MUTATIONS_TESTED_FOR_PARTNUM)) {
				addReasonForInterest("LE-160-2");
				setTermTemplate("[PROPERTY] of [COMPONENT] in [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [CHALLENGE]");
			}
		} else {
			throw new TermServerScriptException("Inheres and Inherent without Inheres in (COMPNUMSUFFIX_PN): " + getExternalIdentifier());
		}

		attributes.addAll(super.determineComponentAttributes(expectNullMap));
		return attributes;
	}
}
