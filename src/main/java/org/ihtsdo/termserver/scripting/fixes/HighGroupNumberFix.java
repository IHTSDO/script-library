package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * INFRA-4792 Seeing concepts using large numbers for groupId
 */
public class HighGroupNumberFix extends BatchFix {

	protected HighGroupNumberFix(BatchFix clone) {
		super(clone);
		additionalReportColumns = "Action Detail";
	}

	public static void main(String[] args) throws TermServerScriptException {
		new HighGroupNumberFix(null).standardExecution(args, ExecutionOptions.DEFAULT);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		super.postInit();
		//standardExecution() forces populateEditPanel false; this fix originally wanted it true
		//(the old main() comment noted: "Actually only found a couple")
		populateEditPanel = true;
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = renumberGroups(task, loadedConcept);
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

	private int renumberGroups(Task t, Concept c) throws TermServerScriptException {
		int changesMade = NO_CHANGES_MADE;
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP, false)) {
			int oldGroupId = g.getGroupId();
			if (oldGroupId > 100) {
				g.setGroupId(SnomedUtils.getFirstFreeGroup(c, CharacteristicType.INFERRED_RELATIONSHIP));
				report(t, c, Severity.LOW, ReportActionType.RELATIONSHIP_MODIFIED, "Group change " + oldGroupId + " -> " + g.getGroupId());
				changesMade++;
				break;
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> processMe = new ArrayList<>();
		for (Concept c : gl.getAllConcepts()) {
			for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP, false)) {
				if (g.getGroupId() > 100) {
					processMe.add(c);
					break;
				}
			}
		}
		return processMe;
	}
}
