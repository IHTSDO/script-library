package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ConcreteValue;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FixConcreteValueDataType extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(FixConcreteValueDataType.class);

	private static final String ECL = "<< " + MEDICINAL_PRODUCT;
	private static Concept TARGET_ATTRIBUTE;

	protected FixConcreteValueDataType(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		FixConcreteValueDataType fix = new FixConcreteValueDataType(null);
		try {
			ReportSheetManager.setTargetFolderId(GFOLDER_TECHNICAL_SPECIALIST);  //Release QA
			fix.populateEditPanel = false;
			fix.selfDetermining = true;
			fix.init(args);
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.postInit();
			TARGET_ATTRIBUTE = fix.getGraphLoader().getConcept("1142139005 |Count of base of active ingredient (attribute)|");
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		//We will not load the concept because the Browser endpoint does not populate the full array of inactivation indicators
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = fixConcreteValueDataType(task, loadedConcept);
			if (changesMade > 0) {
				updateConcept(task, loadedConcept, info);
			}
		} catch (TermServerScriptException e) {
			throw new TermServerScriptException ("Failed to remove duplicate inactivation indicator on " + concept, e);
		}
		return changesMade;
	}

	private int fixConcreteValueDataType(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, TARGET_ATTRIBUTE, ActiveState.ACTIVE)) {
			if (r.isConcrete()) {
				ConcreteValue cv = r.getConcreteValue();
				if (cv.getDataType() != ConcreteValue.ConcreteValueType.INTEGER) {
					cv.setDataType(ConcreteValue.ConcreteValueType.INTEGER);
					changesMade++;
					report(t, c, Severity.LOW, ReportActionType.RELATIONSHIP_MODIFIED, "Concrete value data type updated to INTEGER");
				}
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//Work through all inactive concepts and check the inactivation indicator on all
		//active descriptions
		LOGGER.info("Identifying concepts to process");
		List<Concept> processMe = new ArrayList<>();
		setQuiet(true);
		for (Concept c : findConcepts(ECL)) {
			if (fixConcreteValueDataType(null, c.cloneWithIds()) > 0) {
				processMe.add(c);
			}
		}
		setQuiet(false);
		LOGGER.info("Identified {} concepts to process", processMe.size());
		processMe.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<>(processMe);
	}

}
