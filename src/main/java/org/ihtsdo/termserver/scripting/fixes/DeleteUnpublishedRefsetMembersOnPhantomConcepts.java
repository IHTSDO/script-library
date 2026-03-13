package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ExecutionOptions;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;

import java.util.List;

public class DeleteUnpublishedRefsetMembersOnPhantomConcepts extends BatchFix implements ScriptConstants{

	protected DeleteUnpublishedRefsetMembersOnPhantomConcepts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		ExecutionOptions options = new ExecutionOptions()
				.withNoIntegrityChecking()
				.withImportAllRefsets();
		new DeleteUnpublishedRefsetMembersOnPhantomConcepts(null)
				.standardExecution(args, options);
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		for (RefsetMember m : c.getOtherRefsetMembers()) {
			report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_DELETED, m);
			deleteRefsetMember(t, m.getId());
			changesMade++;
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return gl.getAllConcepts().stream()
				.filter(c -> c.getModuleId() == null)
				.filter(c -> !c.getOtherRefsetMembers().isEmpty())
				.map(c -> (Component)c)
				.toList();
	}




}
