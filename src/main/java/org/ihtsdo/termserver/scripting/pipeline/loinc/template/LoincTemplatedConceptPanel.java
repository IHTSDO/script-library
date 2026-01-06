package org.ihtsdo.termserver.scripting.pipeline.loinc.template;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;

public class LoincTemplatedConceptPanel extends LoincTemplatedConceptWithoutModel {

	private LoincTemplatedConceptPanel(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	private Concept panelParent;

	public static LoincTemplatedConceptPanel create(ExternalConcept externalConcept) throws TermServerScriptException {
		LoincTemplatedConceptPanel panel = new LoincTemplatedConceptPanel(externalConcept);
		panel.createConcept();
		panel.populateTerms();
		return panel;
	}

	@Override
	protected void createConcept() throws TermServerScriptException {
		super.createConcept();
		concept.addRelationship(IS_A, getPanelParent());
	}

	private Concept getPanelParent() throws TermServerScriptException {
		if (panelParent == null) {
			panelParent = gl.getConcept("540081010000107 |Panel (observable entity)|)", true, false);
		}
		return panelParent;
	}

}
