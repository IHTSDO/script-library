package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DeleteUnpublishedDescriptionsInWrongLangRefset extends BatchFix implements ScriptConstants{

	protected DeleteUnpublishedDescriptionsInWrongLangRefset(BatchFix clone) {
		super(clone);
	}

	private static final String WRONG_LANG_REFSET = GB_ENG_LANG_REFSET;

	public static void main(String[] args) throws TermServerScriptException {
		new DeleteUnpublishedDescriptionsInWrongLangRefset(null).standardExecution(args, ExecutionOptions.DEFAULT);
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		for (Description d : descriptionsMeetingCriteria(c)) {
			//Delete the refset members first. These will in inactive if they exist at all
			//but since we're deleting the descriptions, we can't have them hanging around at all.
			deleteDescriptionWithLangRefsets(t, c, d);
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
			if (!d.isReleasedSafely()
					&& d.getLangRefsetEntries(ActiveState.BOTH, WRONG_LANG_REFSET).size() == 1
					&& d.getLangRefsetEntries(ActiveState.BOTH).size() == 1) {
				result.add(d);
			}
		}
		return result;
	}
	
}
