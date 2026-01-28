package org.ihtsdo.termserver.scripting.delta.one_offs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.delta.DeltaGeneratorWithAutoImport;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.util.DrugUtils;

public class INFRA16330_SetDrugsWithUnitsCaseSensitive extends DeltaGeneratorWithAutoImport {

	public static void main(String[] args) throws TermServerScriptException {
		new INFRA16330_SetDrugsWithUnitsCaseSensitive().standardExecution(args);
	}

	public void process() throws TermServerScriptException {
		for (Concept c : MEDICINAL_PRODUCT.getDescendants(NOT_SET)) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE))	{
				if (d.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE) &&
					containsCaseSensitiveUnit(d)) {
					d.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
					d.setEffectiveTime(null);
					report(c, d, "ci -> cI due to presence of case sensitive unit");
					incrementSummaryInformation("Descriptions changed to cI");
				}
			}
		}
	}

	private boolean containsCaseSensitiveUnit(Description d) {
		for (DrugUtils.MatchingSet ms : DrugUtils.KNOWN_CASE_SENSITIVE_DRUG_UNITS) {
			if (DrugUtils.containsTargetText(d, ms)) {
				return true;
			}
		}
		return false;
	}

}
