package org.ihtsdo.termserver.scripting.pipeline.loinc.template;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConceptNull;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincTerm;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConcept;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConceptNull;

public class LoincTemplatedConceptWithProcessNoOutput extends LoincTemplatedConceptWithProcess {

	private static Concept characterizes;

	private LoincTemplatedConceptWithProcessNoOutput(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	public static TemplatedConcept create(ExternalConcept externalConcept) throws TermServerScriptException {
		//Watch out that we might just pass in a dummy concept to test for it being inScope.
		if (!(externalConcept instanceof ExternalConceptNull)) {
			//If this is a RelAcnc concept, then we only want it if the class is COAG
			LoincTerm lt = (LoincTerm) externalConcept;
			if (lt.getProperty().equals("RelAcnc") && !lt.getClass().equals("COAG")) {
				return TemplatedConceptNull.create(externalConcept);
			}
		}

		LoincTemplatedConceptWithProcessNoOutput templatedConcept = new LoincTemplatedConceptWithProcessNoOutput(externalConcept);
		templatedConcept.populateTypeMapCommonItems();
		if (characterizes == null) {
			characterizes = gl.getConcept("704321009 |Characterizes (attribute)|");
		}
		templatedConcept.typeMap.put(LOINC_PART_TYPE_COMPONENT, characterizes);

		//See https://confluence.ihtsdotools.org/display/SCTEMPLATES/Process+Observable+for+LOINC+-+No+Process+output%2C+With+Time+Aspect+%28observable+entity%29+-+v0.1
		//[property] of [characterizes] of [process output] in [process duration] in [direct site] by [technique] using [using device] [precondition] (observable entity)
		templatedConcept.setTermTemplate("[PROPERTY] of [COMPONENT] at [TIME] in [SYSTEM] by [METHOD] using [DEVICE] [CHALLENGE]");
		return templatedConcept;
	}

}
