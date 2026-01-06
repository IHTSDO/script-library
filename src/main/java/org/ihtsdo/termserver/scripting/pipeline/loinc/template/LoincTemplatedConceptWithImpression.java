package org.ihtsdo.termserver.scripting.pipeline.loinc.template;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;

public class LoincTemplatedConceptWithImpression extends LoincTemplatedConceptWithoutModel {

	private LoincTemplatedConceptWithImpression(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	public static LoincTemplatedConceptWithImpression create(ExternalConcept externalConcept) throws TermServerScriptException {
		LoincTemplatedConceptWithImpression templatedConcept = new LoincTemplatedConceptWithImpression(externalConcept);
		templatedConcept.createConcept();
		return templatedConcept;
	}

	@Override
	protected void createConcept() throws TermServerScriptException {
		super.createConcept();
		concept.addRelationship(IS_A, OBSERVABLE_ENTITY);

		RelationshipTemplate propertyAttribute =  new RelationshipTemplate(
				gl.getConcept("370130000 |Property (attribute)|"),
				gl.getConcept("118580000 |Impression AND/OR interpretation of study (property) (qualifier value)|")
		);
		concept.addRelationship(propertyAttribute, GROUP_1);
	}

}
