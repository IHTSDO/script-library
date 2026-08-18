package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.GraphLoader.DuplicatePair;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ExecutionOptions;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DuplicateLangRefsetsFix extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(DuplicateLangRefsetsFix.class);

	protected DuplicateLangRefsetsFix(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		new DuplicateLangRefsetsFix(null).standardExecution(args, ExecutionOptions.DEFAULT);
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			//Find all refsetIds to be deleted for this concept
			for (DuplicatePair dups : gl.getDuplicateLangRefsetEntriesMap().get(c)) {
				LangRefsetEntry l1 = (LangRefsetEntry)dups.getKeep();
				LangRefsetEntry l2 = (LangRefsetEntry)dups.getInactivate();
				report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, l2.toString(true));
				if (StringUtils.isEmpty(l2.getEffectiveTime())) {
					changesMade += deleteRefsetMember(t, l2.getId());
					//What if l1 was made inactive?  Need to reactivate
					if (!l1.isActive() && l2.isActive()) {
						l1.setActive(true);
						changesMade += updateRefsetMember(t, l1, info);
						report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REACTIVATED, l1.toString(true));
					} else {
						report(t, c, Severity.LOW, ReportActionType.NO_CHANGE, l1.toString(true));
					}
				} else {
					l2.setActive(false);
					changesMade += updateRefsetMember(t, l2, info);
					report(t, c, Severity.LOW, ReportActionType.NO_CHANGE, l1.toString(true));
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
		return new ArrayList<>(gl.getDuplicateLangRefsetEntriesMap().keySet());
	}

}
