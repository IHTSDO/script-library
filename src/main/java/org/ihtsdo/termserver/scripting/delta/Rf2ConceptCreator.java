package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.util.List;
import java.util.UUID;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class Rf2ConceptCreator extends DeltaGeneratorWithAutoImport {

	public static Rf2ConceptCreator build(TermServerScript clone, String[] args) throws TermServerScriptException {
		Rf2ConceptCreator conceptCreator = Rf2ConceptCreator.preBuild(clone, args);
		if (clone instanceof DeltaGenerator deltaGenerator) {
			conceptCreator.conIdGenerator = deltaGenerator.conIdGenerator;
			conceptCreator.descIdGenerator = deltaGenerator.descIdGenerator;
			conceptCreator.relIdGenerator = deltaGenerator.relIdGenerator;
		}
		conceptCreator.setReportName(clone.getClass().getSimpleName());
		return conceptCreator;
	}

	public static Rf2ConceptCreator build(TermServerScript clone) throws TermServerScriptException {
		Rf2ConceptCreator conceptCreator = Rf2ConceptCreator.preBuild(clone, null);
		if (clone instanceof DeltaGenerator deltaGenerator) {
			conceptCreator.conIdGenerator = deltaGenerator.conIdGenerator;
			conceptCreator.descIdGenerator = deltaGenerator.descIdGenerator;
			conceptCreator.relIdGenerator = deltaGenerator.relIdGenerator;
		}
		conceptCreator.setReportName(clone.getClass().getSimpleName());
		return conceptCreator;
	}

	private static Rf2ConceptCreator preBuild(TermServerScript clone, String[] args)  throws TermServerScriptException {
		Rf2ConceptCreator conceptCreator = new Rf2ConceptCreator();
		if (clone != null) {
			conceptCreator.copyScriptState(clone);
		}
		conceptCreator.initialiseOutputDirectory();
		conceptCreator.initialiseFileHeaders();

		if (args != null && args.length > 0) {
			conceptCreator.initialiseDeltaGeneratorSpecifics(args);
		}
		return conceptCreator;
	}

	public void writeConceptsToRF2(int tabIdx, List<Concept> concepts) throws TermServerScriptException {
		for (Concept concept : concepts) {
			writeConceptToRF2(tabIdx, concept, "");
		}
	}

	public Concept writeConceptToRF2(int tabIdx, Concept concept, String info) throws TermServerScriptException {
		concept.setId(null);
		populateIds(concept);
		outputRF2(tabIdx, concept, info);  //Will only output dirty fields.
		return concept;
	}
	
	public void outputRF2(int tabIdx, Concept concept, String info) throws TermServerScriptException {
		//Populate expression now because rels turn to axioms when we output
		String expression = concept.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		if (super.outputRF2(concept)) {
			incrementSummaryInformation("Concepts output to RF2");
			report(tabIdx, concept, Severity.LOW, ReportActionType.CONCEPT_ADDED, info, SnomedUtils.getDescriptions(concept), expression, "OK");
		}
	}

	public void outputRF2Inactivation(Concept concept) throws TermServerScriptException {
		//We'll do inactivations quietly
		//We only want to output inactive components, so set everything else clean
		for (Component c : SnomedUtils.getAllComponents(concept)) {
			if (c.isActiveSafely()) {
				c.setClean();
			}
		}
		super.outputRF2(concept);
	}


	public void populateIds(Concept concept) throws TermServerScriptException {
		for (Component c : SnomedUtils.getAllComponents(concept, true)) {
			populateComponentId(concept, c, targetModuleId);
		}
	}
	
	public void populateComponentId(Concept concept, Component c, String enforceModule) throws TermServerScriptException {
		if (enforceModule != null) {
			c.setModuleId(enforceModule);
		}
		c.setDirty();
		
		switch (c.getComponentType()) {
			case CONCEPT : setConceptId(c);
				break;
			case DESCRIPTION : setDescriptionId(concept.getId(), c);
				alignLangRefsetEntries((Description)c);
				break;
			case INFERRED_RELATIONSHIP :
				setRelationshipId(c);
				ensureRelationshipPartsHaveIds(concept, (Relationship)c, enforceModule);
				break;
			case STATED_RELATIONSHIP :
				//No need to do anything here because we'll convert
				//stated to an axiom and we're not expecting any inferred
				break;
			case ALTERNATE_IDENTIFIER :
				break;  //Has its own ID.  RefCompId will be set once concept id is known.
			default: c.setId(UUID.randomUUID().toString());
		}
	}

	private void alignLangRefsetEntries(Description d) {
		for (LangRefsetEntry l : d.getLangRefsetEntries()){
			l.setReferencedComponentId(d.getId());
			l.setDirty();
		}
	}

	private void ensureRelationshipPartsHaveIds(Concept c, Relationship r, String enforceModule) throws TermServerScriptException {
		r.setSource(c);
		if (c.getId() == null) {
			populateComponentId(c, c, enforceModule);
		}

		Concept type = r.getType();
		if (type.getId() == null) {
			populateComponentId(c, type, enforceModule);
		}

		Concept target = r.getTarget();
		if (target.getId() == null) {
			populateComponentId(c, target, enforceModule);
		}
	}

	private void setConceptId(Component component) throws TermServerScriptException {
		Concept c = (Concept)component;
		String conceptId = c.getConceptId();
		//Populate the concept ID if it's missing or if it's a temporary UUID
		if (conceptId == null || conceptId.length() > SCTID_MAX_LENGTH) {
			conceptId = conIdGenerator.getSCTID();
			c.setId(conceptId);
		}

		String finalConceptId = conceptId;
		c.getDescriptions().forEach(d -> d.setConceptId(finalConceptId));
		c.getRelationships().forEach(r -> r.setSourceId(finalConceptId));
		c.getAlternateIdentifiers().forEach(i -> i.setReferencedComponentId(finalConceptId));
		c.getComponentAnnotationEntries().forEach(a -> a.setReferencedComponentId(finalConceptId));
		c.getOtherRefsetMembers().forEach(rm -> rm.setReferencedComponentId(finalConceptId));
	}
	
	private void setDescriptionId(String conceptId, Component component) throws TermServerScriptException {
		Description d = (Description)component;
		d.setConceptId(conceptId);
		String descId = d.getId();
		if (descId == null) {
			descId = descIdGenerator.getSCTID();
			d.setId(descId);
		}

		if (d.getConceptId() == null) {
			throw new TermServerScriptException("Description " + d + " has no concept ID");
		}

		String finalDescId = descId;
		d.getLangRefsetEntries().stream()
			.forEach(l -> {
				l.setReferencedComponentId(finalDescId);
				if (l.getId() == null) {
					l.setId(UUID.randomUUID().toString());
				}
			});
	}
	
	private void setRelationshipId(Component component) throws TermServerScriptException {
		Relationship r = (Relationship)component;
		String relId = r.getRelationshipId();
		if (relId == null) {
			relId = relIdGenerator.getSCTID();
			r.setId(relId);
		}
	}

	@Override
	public void finish() {
		closeIdGenerators();
	}

	public void createOutputArchive(int tabIdx) throws TermServerScriptException {
		getRF2Manager().flushFiles(true); //Just flush the RF2, we might want to keep the report going
		File archive = SnomedUtils.createArchive(new File(outputDirName));
		report(tabIdx, "");
		report(tabIdx, ReportActionType.INFO, "Created " + archive.getName());

		importArchiveToTask(archive);
	}

	public void copyStatedRelsToInferred(Concept c) {
		for (Relationship statedRel : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			Relationship infRel = statedRel.clone();
			infRel.setCharacteristicType(CharacteristicType.INFERRED_RELATIONSHIP);
			infRel.setAxiom(null);
			infRel.setAxiomEntry(null);
			infRel.setDirty();
			infRel.setModuleId(c.getModuleId());
			c.addRelationship(infRel);
		}
	}

	public String getTargetModuleId() {
		return targetModuleId;
	}

	public void outputAltId(Concept c, String schemeId) throws TermServerScriptException {
		for (AlternateIdentifier altId : c.getAlternateIdentifiers()) {
			if (altId.getIdentifierSchemeId().equals(schemeId)) {
				altId.setDirty();
				writeToRF2File(altIdDeltaFilename, altId.toRF2());
			}
		}
	}
}
