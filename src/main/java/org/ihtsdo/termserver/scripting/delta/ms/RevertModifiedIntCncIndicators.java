package org.ihtsdo.termserver.scripting.delta.ms;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.InactivationIndicatorEntry;

/**
 * Where a CNC indicator has been inactivated in the International Edition due to the _description_
 * also being inactivated, then Snowstorm might revert that change if a concept save is done
 * in a country extension.
 * Inactivated descriptions should have proper inactivation indicators, not CNC ones, but
 * we'll just put things back the way they were
 **/
public class RevertModifiedIntCncIndicators extends DeltaGenerator {

	public static void main(String[] args) throws TermServerScriptException {
		new RevertModifiedIntCncIndicators().standardExecution(args);
	}

	@Override
	public void process() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			for (Description d : c.getDescriptions()) {
				//Does this description have an active cnc indicator, with no effective time, in the core module?
				for (InactivationIndicatorEntry ii : d.getInactivationIndicatorEntries()) {
					if (ii.isActiveSafely() && ii.getEffectiveTime().isEmpty() && ii.getModuleId().equals(SCTID_CORE_MODULE)) {
						ii.setActive(false, true);
						report(c, Severity.LOW, ReportActionType.REFSET_MEMBER_INACTIVATED, ii);
					}
				}
			}
		}
	}

}
