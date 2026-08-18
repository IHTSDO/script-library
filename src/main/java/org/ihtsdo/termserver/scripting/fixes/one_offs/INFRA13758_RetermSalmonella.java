package org.ihtsdo.termserver.scripting.fixes.one_offs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

import java.util.*;

public class INFRA13758_RetermSalmonella extends BatchFix {

	String search = "Salmonella III arizonae";
	String replace = "Salmonella enterica subsp. diarizonae";

	protected INFRA13758_RetermSalmonella(BatchFix clone) {
		super(clone);
		populateTaskDescription = true;
		reportNoChange = true;
		additionalReportColumns = "Action Detail, Additional Detail";
	}

	public static void main(String[] args) throws TermServerScriptException {
		new INFRA13758_RetermSalmonella(null).standardExecution(args, ExecutionOptions.DEFAULT);
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
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = modifyDescriptions(task, loadedConcept);
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

	private int modifyDescriptions(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.getTerm().contains(search)) {
				String replacement = d.getTerm().replace(search, replace);
				replaceDescription(t, c, d, replacement, InactivationIndicator.OUTDATED, true);
				changesMade++;
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> process = new ArrayList<>();
		for (Concept c : ORGANISM.getDescendants(NOT_SET)) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (d.getTerm().contains(search)) {
					process.add(c);
					break;
				}
			}
		}
		return process;
	}
}
