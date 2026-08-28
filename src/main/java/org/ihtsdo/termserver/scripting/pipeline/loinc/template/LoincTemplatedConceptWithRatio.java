package org.ihtsdo.termserver.scripting.pipeline.loinc.template;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConceptNull;

public class LoincTemplatedConceptWithRatio extends LoincTemplatedConceptWithRelative {

	private LoincTemplatedConceptWithRatio(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	private static final String RATIO_SLOTS = "[COMPONENT]" + SEPARATOR + "[DIVISORS]";

	@Override
	protected Concept getParentConceptForTemplate() throws TermServerScriptException {
		return gl.getConcept("540131010000107 |Ratio observable (observable entity)|");
	}

	public static LoincTemplatedConcept create(ExternalConcept externalConcept) throws TermServerScriptException {
		LoincTemplatedConceptWithRatio templatedConcept = new LoincTemplatedConceptWithRatio(externalConcept);
		if (!(externalConcept instanceof ExternalConceptNull)) {
			templatedConcept.populateTypeMapCommonItems();
			templatedConcept.typeMap.put("DIVISORS", gl.getConcept("704325000 |Relative to (attribute)|"));
			//The 'to' changes to a slash in the PT
			String termTemplate = "[PROPERTY] of " + RATIO_SLOTS + " in [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [CHALLENGE]";
			templatedConcept.setTermTemplate(templatedConcept.addAdjustmentToTermTemplate(termTemplate));
		}
		return templatedConcept;
	}

}
