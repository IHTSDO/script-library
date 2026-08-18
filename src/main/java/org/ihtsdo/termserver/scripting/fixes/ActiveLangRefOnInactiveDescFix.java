package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.termserver.scripting.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ISRS-979 Fix for active langrefsetentries being left on inactive descriptions 
 *
 */
public class ActiveLangRefOnInactiveDescFix extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(ActiveLangRefOnInactiveDescFix.class);
	
	protected ActiveLangRefOnInactiveDescFix(BatchFix clone) {
		super(clone);
		this.populateTaskDescription = false;
	}

	public static void main(String[] args) throws TermServerScriptException {
		new ActiveLangRefOnInactiveDescFix(null).standardExecution(args, ExecutionOptions.DEFAULT);
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			for (Description d : c.getDescriptions()) {
				if (!d.isActiveSafely() && inScope(d)) {
					//Does it still have an active language refset?
					for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.ACTIVE)) {
						l.setActive(false);
						changesMade += updateRefsetMember(t, l, info);
						report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, l);
					}
				}
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to update refset entry for " + c, e);
		}
		return changesMade;
	}
	
	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		LOGGER.info("Identifying concepts to process...");
		List<Component> componentsToProcess = new ArrayList<>();
		//Looking for inactive descriptions appropriately scoped
		for (Concept c : gl.getAllConcepts()) {
			//Does it still have an active language refset?
			for (Description d : c.getDescriptions()) {
				if (!d.isActiveSafely() && inScope(d) &&
						!d.getLangRefsetEntries(ActiveState.ACTIVE).isEmpty()) {
					componentsToProcess.add(c);
					break;
				}
			}
		}
		return componentsToProcess;
	}

}
