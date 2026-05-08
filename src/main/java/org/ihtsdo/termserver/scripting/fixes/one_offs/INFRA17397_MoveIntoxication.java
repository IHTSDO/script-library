package org.ihtsdo.termserver.scripting.fixes.one_offs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

public class INFRA17397_MoveIntoxication extends BatchFix implements ScriptConstants {

	protected INFRA17397_MoveIntoxication(BatchFix clone) {
		super(clone);
	}

	Concept intoxication;

	public static void main(String[] args) throws TermServerScriptException {
		new INFRA17397_MoveIntoxication(null).standardExecution(args, ExecutionOptions.DEFAULT);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		intoxication = gl.getConcept("1149322001 |Intoxication|");
		subsetECL = "<< " + intoxication;
		super.postInit();
	}

	@Override
	public int doFix(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		//If this is the top level concepts, then we'll change its parent.  All other concepts will already
		//be pointing to the top level, so after that, all we need to do is change the semantic tags
		if (c.equals(intoxication)) {
			changesMade += replaceParent(t, c, DISEASE, CLINICAL_FINDING);
		}
		changesMade += replaceSemanticTag(t, c);
		return changesMade;
	}

	private int replaceSemanticTag(Task t, Concept c) throws TermServerScriptException {
		Description fsn = c.getFSNDescription();
		String oldTerm = fsn.getTerm();
		if (!oldTerm.contains("(disorder)")) {
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "FSN does not contain disorder semantic tag");
			return NO_CHANGES_MADE;
		}
		String newTerm = oldTerm.replace("(disorder)", "(finding)");
		replaceDescription(t, c, fsn, newTerm, InactivationIndicator.OUTDATED);
		return CHANGE_MADE;
	}

}
