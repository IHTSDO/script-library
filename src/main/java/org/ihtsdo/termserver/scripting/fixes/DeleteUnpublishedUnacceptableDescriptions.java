package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.*;

import java.util.*;
import java.util.stream.Collectors;

public class DeleteUnpublishedUnacceptableDescriptions extends BatchFix implements ScriptConstants{

	protected DeleteUnpublishedUnacceptableDescriptions(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		new DeleteUnpublishedUnacceptableDescriptions(null).standardExecution(args, ExecutionOptions.DEFAULT);
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		for (Description d : descriptionsMeetingCriteria(c)) {
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
			if (!d.isReleasedSafely() && d.getLangRefsetEntries(ActiveState.ACTIVE).isEmpty()) {
				result.add(d);
			}
		}
		return result;
	}


}
