package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.*;

import java.util.ArrayList;
import java.util.List;

public class DeleteUnpublishedLangRefsetMembersForDialect extends BatchFix implements ScriptConstants{

	private static final String UNWANTED_LANG_REFSET = GB_ENG_LANG_REFSET;

	protected DeleteUnpublishedLangRefsetMembersForDialect(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		new DeleteUnpublishedLangRefsetMembersForDialect(null).standardExecution(args, ExecutionOptions.DEFAULT);
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		for (Description d : descriptionsMeetingCriteria(c)) {
			for (LangRefsetEntry lr : d.getLangRefsetEntries(ActiveState.BOTH, UNWANTED_LANG_REFSET)) {
				if (!lr.isReleasedSafely()) {
					deleteRefsetMember(t, lr.getId(), false);
					report(t, c, Severity.LOW, ReportActionType.LANG_REFSET_DELETED, lr);
				}
			}
		}
		return CHANGE_MADE;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return gl.getAllConcepts().stream()
				.filter(c -> !descriptionsMeetingCriteria(c).isEmpty())
				.map(c -> (Component)c)
				.toList();
	}

	private List<Description> descriptionsMeetingCriteria(Concept c) {
		List<Description> result = new ArrayList<>();
		for (Description d : c.getDescriptions()) {
			for (LangRefsetEntry lr : d.getLangRefsetEntries(ActiveState.BOTH, UNWANTED_LANG_REFSET)) {
				if (!lr.isReleasedSafely()) {
					result.add(d);
					break;
				}
			}
		}
		return result;
	}


}
