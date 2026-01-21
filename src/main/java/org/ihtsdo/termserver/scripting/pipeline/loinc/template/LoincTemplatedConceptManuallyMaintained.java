package org.ihtsdo.termserver.scripting.pipeline.loinc.template;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;

public class LoincTemplatedConceptManuallyMaintained extends LoincTemplatedConcept {

	protected LoincTemplatedConceptManuallyMaintained(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	public static LoincTemplatedConcept create(ExternalConcept externalConcept) throws TermServerScriptException {
		return new LoincTemplatedConceptManuallyMaintained(externalConcept);
	}
}
