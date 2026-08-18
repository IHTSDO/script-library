package org.ihtsdo.termserver.scripting.fixes;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;

/**
 * NUTRITION-58 Inactivate attributes matching a particular patern
 */
public class InactivateAttributes extends BatchFix {

	private RelationshipTemplate inactivateTemplate;
	
	protected InactivateAttributes(BatchFix clone) {
		super(clone);
		this.reportNoChange = true;
		this.additionalReportColumns = "Action Detail";
	}

	public static void main(String[] args) throws TermServerScriptException {
		new InactivateAttributes(null).standardExecution(args, ExecutionOptions.DEFAULT);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		subsetECL = "<< 364393001 |Nutritional observable (observable entity)| :  370132008 |Scale type (attribute)| = 30766002 |Quantitative (qualifier value)| ";
		inactivateTemplate = new RelationshipTemplate(gl.getConcept("370132008 |Scale type (attribute)|"),
				gl.getConcept("30766002 |Quantitative (qualifier value)|"));
		super.postInit();
		//standardExecution() forces populateEditPanel false; this fix originally wanted it true
		populateEditPanel = true;
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade += inactivateAttribute(task, loadedConcept);
			if (changesMade > 0) {
				updateConcept(task, loadedConcept, info);
			}
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}

	private int inactivateAttribute(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Relationship r : c.getRelationships(inactivateTemplate, ActiveState.ACTIVE)) {
			changesMade += removeRelationship(t, c, r);
		}
		return changesMade;
	}
}
