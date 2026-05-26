package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.ExecutionOptions;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.ihtsdo.termserver.scripting.util.AcceptabilityMode;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import java.util.Map;

public class CreateDialectEquivalentDescriptionDriven extends BatchFix implements ScriptConstants {

	protected CreateDialectEquivalentDescriptionDriven(BatchFix clone) {
		super(clone);
	}

	Map<String, String> dialectReplacement = Map.of("sulfate", "sulphate");
	Map<String, Acceptability> targetAcceptability = SnomedUtils.createAcceptabilityMap(AcceptabilityMode.ACCEPTABLE_GB);

	public static void main(String[] args) throws TermServerScriptException {
		new CreateDialectEquivalentDescriptionDriven(null).standardExecution(args, new ExecutionOptions().withDrivenByInputFile());
	}

	@Override
	public int doFix(Task t, Concept c) throws TermServerScriptException {
		//Take the US preferred term.   Check it is the same as the GB preferred term
		//Do the substitution and add as a new enGB acceptable term
		Description enUSPT = c.getPreferredSynonym(RF2Constants.US_ENG_LANG_REFSET);
		String term = enUSPT.getTerm();
		for (Map.Entry<String, String> entry : dialectReplacement.entrySet()) {
			term = term.replace(entry.getKey(), entry.getValue());
		}
		Description newDesc = Description.withDefaults(term, DescriptionType.SYNONYM, targetAcceptability);
		//Copy the case significance from the original
		newDesc.setCaseSignificance(enUSPT.getCaseSignificance());
		addDescription(t, c, newDesc, true);
		return CHANGE_MADE;
	}

}
