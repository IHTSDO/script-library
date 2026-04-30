package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ExecutionOptions;

public class ListRefsetSubset extends TermServerReport {

	static final String REFSET_ID = "447562003 |SNOMED CT to ICD10 Map|";
	static final String ECL = "<<52448006 |Dementia (disorder)|";

	private Concept refsetOfInterest;
	
	public static void main(String[] args) throws TermServerScriptException {
		ExecutionOptions options = new ExecutionOptions().withImportAllRefsets();
		new ListRefsetSubset().standardExecution(args, options);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		refsetOfInterest = gl.getConcept(REFSET_ID);
		String[] columnHeadings = new String[] { "SCTID, FSN, Semtag, Active, " + findAdditionalRefsetColumns()};
		String[] tabNames = new String[] {REFSET_ID + " filtered by " + ECL };
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	public void runJob() throws TermServerScriptException {
		for (Concept c : findConcepts(ECL)) {
			for (RefsetMember rm : c.getOtherRefsetMembers(refsetOfInterest.getId())) {
				report(c, c.isActiveSafely()? "Y":"N", rm.getAdditionalFieldsAsArray());
			}
		}
	}

	private String findAdditionalRefsetColumns() {
		return gl.getAllConcepts().stream()
				.map(c -> c.getOtherRefsetMembers(refsetOfInterest.getId()))
				.filter(list -> !list.isEmpty())
				.map(list -> list.get(0)) // first RefsetMember for that concept
				.map(RefsetMember::getAdditionalFieldNames)
				.findFirst() // first concept containing at least one member
				.map(fields -> String.join(", ", fields))
				.orElse("");
	}
}
