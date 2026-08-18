package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ExecutionOptions;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import java.util.List;

public class InactivateSimpleRefsetMembersWithInactiveReferencedComponents extends BatchFix {

	protected InactivateSimpleRefsetMembersWithInactiveReferencedComponents(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		new InactivateSimpleRefsetMembersWithInactiveReferencedComponents(null).standardExecution(args, ExecutionOptions.DEFAULT.withImportAllRefsets());
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			for (RefsetMember rm : c.getOtherRefsetMembers()) {
				rm.setActive(false);
				changesMade += updateRefsetMember(t, rm, info);
				report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_INACTIVATED, rm);
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to update refset entry for " + c, e);
		}
		return changesMade;
	}
	
	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return gl.getAllConcepts().stream()
				.filter(c -> !c.isActiveSafely())
				.filter(this::inScope)
				.filter(c -> !c.getOtherRefsetMembers().isEmpty())
				.sorted(SnomedUtils::compareSemTagFSN)
				.map(c -> (Component)c)
				.toList();
	}
}
