package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;

/*
For DRUG-422, DRUG-431
Driven by a text file of concepts, move specified concepts to exist under
a parent concept.
*/
public class ReplaceParentsDriven extends BatchFix implements ScriptConstants{

	private Relationship newParentRel;
	private String newParent = "685451010000100"; // |Measurement property (qualifier value)|
	private static final boolean SET_CONCEPTS_SD = false;
	private Concept allowAdditionalParentsExcept;
	
	protected ReplaceParentsDriven(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		ExecutionOptions options = new ExecutionOptions().withDrivenByInputFile();
		new ReplaceParentsDriven(null).standardExecution(args, options);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		Concept parentConcept =  gl.getConcept(newParent);
		newParentRel = new Relationship(null, IS_A, parentConcept, 0);
		additionalReportColumns = "Action Detail, Definition Status, Parent Count, Attribute Count, Additional Detail";
		allowAdditionalParentsExcept = gl.getConcept("118598001 |Property (qualifier value)|");
		super.postInit();
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = replaceParents(t, loadedConcept);

		if (SET_CONCEPTS_SD && loadedConcept.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
			if (countAttributes(loadedConcept, CharacteristicType.STATED_RELATIONSHIP) > 0) {
				loadedConcept.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
				changesMade++;
				report(t, loadedConcept, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept marked as fully defined");
			} else {
				report(t, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Unable to mark fully defined - no attributes!");
			}
		}
		
		updateConcept(t, loadedConcept, info);
		return changesMade;
	}

	private int replaceParents(Task task, Concept loadedConcept) throws TermServerScriptException {
		int changesMade = 0;
		Set<Relationship> parentRels = new HashSet<> (loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP,
																		IS_A,
																		ActiveState.ACTIVE));
		String parentCount = Integer.toString(parentRels.size());
		String attributeCount = Integer.toString(countAttributes(loadedConcept, CharacteristicType.STATED_RELATIONSHIP));

		boolean replacementNeeded = true;
		for (Relationship parentRel : parentRels) {
			//If this is the only relationship, and it's already the new target, then we don't need to make any changes
			if (parentRel.equals(newParentRel) && parentRels.size() == 1) {
				replacementNeeded = false;
			}

			if (allowAdditionalParentsExcept == null || parentRel.getTarget().equals(allowAdditionalParentsExcept)) {
				removeParentRelationship(task, parentRel, loadedConcept, newParentRel.getTarget().toString(), null);
				changesMade++;
			}
		}
		
		if (replacementNeeded) {
			Relationship thisNewParentRel = newParentRel.clone(null);
			thisNewParentRel.setSource(loadedConcept);
			loadedConcept.addRelationship(thisNewParentRel);
			changesMade++;
			String msg = "Single parent set to " + newParent;
			report(task, loadedConcept, Severity.LOW, ReportActionType.RELATIONSHIP_INACTIVATED, msg, loadedConcept.getDefinitionStatus().toString(), parentCount, attributeCount);
		}
		return changesMade;
	}

}
