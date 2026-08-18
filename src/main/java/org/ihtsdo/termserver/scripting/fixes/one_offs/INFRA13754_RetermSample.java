package org.ihtsdo.termserver.scripting.fixes.one_offs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.ExecutionOptions;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

public class INFRA13754_RetermSample extends BatchFix {

	String search = "sample";
	String replace = "specimen";

	String searchUpperCase = "Sample";
	String replaceUpperCase = "Specimen";

	protected INFRA13754_RetermSample(BatchFix clone) {
		super(clone);
		populateTaskDescription = true;
		reportNoChange = true;
		inputFileHasHeaderRow = true;
		additionalReportColumns = "Action Detail, Additional Detail";
	}

	public static void main(String[] args) throws TermServerScriptException {
		new INFRA13754_RetermSample(null).standardExecution(args, ExecutionOptions.DEFAULT);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		super.postInit();
		//standardExecution forces populateEditPanel to false; this fix relies on the edit panel being populated
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
			if (d.getTerm().toLowerCase().contains(search)) {
				String replacement = getReplacementMatchingCase(d);
				replaceDescription(t, c, d, replacement, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY, true);
				changesMade++;
			}
		}
		return changesMade;
	}

	private String getReplacementMatchingCase(Description d) {
		String replacement = d.getTerm().replace(search, replace);
		if (d.getTerm().equals(replacement)) {
			replacement = d.getTerm().replace(searchUpperCase, replaceUpperCase);
		}
		return replacement;
	}
}
