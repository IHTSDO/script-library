package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ExecutionOptions;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class InactivateMembersOnInactiveRefsets extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(InactivateMembersOnInactiveRefsets.class);

	protected InactivateMembersOnInactiveRefsets(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		new InactivateMembersOnInactiveRefsets(null).standardExecution(args, ExecutionOptions.DEFAULT.withImportAllRefsets());
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			for (RefsetMember rm : c.getOtherRefsetMembers()) {
				Concept refset = gl.getConcept(rm.getRefsetId());
				if (!refset.isActive()) {
					rm.setActive(false);
					changesMade += updateRefsetMember(t, rm, info);
					report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_INACTIVATED, refset, rm);
				}
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to update refset entry for " + c, e);
		}
		return changesMade;
	}
	
	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		LOGGER.info("Identifying concepts to process");
		List<Component> processMe = new ArrayList<>();

		nextConcept:
		for (Concept c : gl.getAllConcepts()) {
			for (RefsetMember rm : c.getOtherRefsetMembers()) {
				Concept refset = gl.getConcept(rm.getRefsetId());
				if (!refset.isActive() && inScope(refset)) {
					processMe.add(c);
					continue nextConcept;
				}
			}
		}
		return processMe;
	}

}
