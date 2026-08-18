package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.otf.exception.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.ExecutionOptions;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;

import java.util.List;

public class DeleteBornInactiveComponents extends BatchFix implements ScriptConstants {

	protected DeleteBornInactiveComponents(BatchFix clone) {
		super(clone);
		this.populateTaskDescription = false;
		this.worksWithConcepts = false; //Ensures doFix is called with Component
	}

	public static void main(String[] args) throws TermServerScriptException {
		new DeleteBornInactiveComponents(null).standardExecution(args, ExecutionOptions.DEFAULT);
	}

	@Override
	public int doFix(Task t, Component c, String info) throws TermServerScriptException {
		if (c instanceof Relationship r) {
			deleteRelationship(t, r);
		} else if (c instanceof RefsetMember) {
			throw new NotImplementedException();
		} else if (c instanceof Concept) {
			throw new NotImplementedException();
		} else if (c instanceof Description) {
			throw new NotImplementedException();
		} else {
			throw new TermServerScriptException("Unable to delete component of type " + c.getClass().getSimpleName());
		}
		return CHANGE_MADE;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return gl.getAllComponents().stream()
				.filter(c -> !c.isReleased())
				.filter(c -> !c.isActive())
				.toList();
	}
	
}
