package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import java.util.*;


public class AssignConceptsToTasks extends BatchFix {

	private String subsetECL = " (<<277132007 |Therapeutic procedure (procedure)| : 363703001 |Has intent (attribute)| = <<262202000 |Therapeutic intent (qualifier value)| ) MINUS << 416608005 |Drug therapy (procedure)|";

	protected AssignConceptsToTasks(BatchFix clone) {
		super(clone);
		taskPrefix = "TherapeuticProc_";
		taskThrottle = 0;  //No need to slow down, not taking up any time on the Terminology Server
		additionalReportColumns = "ActionDetail, Descriptions, Inferred Expression";
		conceptThrottle = 0;
		reportNoChange = false;
	}

	public static void main(String[] args) throws TermServerScriptException {
		new AssignConceptsToTasks(null).standardExecution(args, ExecutionOptions.DEFAULT);
	}

	@Override
	protected void init(String[] args) throws TermServerScriptException {
		//standardExecution() forces runStandAlone false before init() resolves the project;
		//this fix needs it true, and postInit() runs too late to affect that resolution
		runStandAlone = true;
		super.init(args);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		super.postInit();
		//standardExecution() forces populateEditPanel false; this fix originally wanted it true
		populateEditPanel = true;
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			report(task, concept, Severity.LOW, ReportActionType.INFO, "Assigning concept to task", SnomedUtils.getDescriptions(concept), concept.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> toProcess = new ArrayList<>();
		Set<Concept> theraputicIntentConcepts = gl.getConcept("262202000").getDescendants(NOT_SET);
		Concept HAS_INTENT = gl.getConcept("363703001");

		nextConcept:
		for (Concept c : SnomedUtils.sort(findConcepts(subsetECL, true, true))) {
			//INFRA-12736 We're only interested in concepts that have 363703001 |Has intent (attribute)| = <<262202000 |Therapeutic intent (qualifier value)| ungrouped
			for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP)) {
				for (Relationship r : g.getRelationships()) {
					if (r.getType().equals(HAS_INTENT) && theraputicIntentConcepts.contains(r.getTarget())
						&& g.size() == 1) {
						toProcess.add(c);
						continue nextConcept;
					}
				}
			}
		}
		return toProcess;
	}
}
