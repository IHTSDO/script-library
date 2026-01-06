package org.ihtsdo.termserver.scripting.delta.one_offs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.delta.DeltaGeneratorWithAutoImport;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.snapshot.ArchiveImporter;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class MAINT2904_InactivateCNCIndicators extends DeltaGeneratorWithAutoImport {

	public static void main(String[] args) throws TermServerScriptException {
		MAINT2904_InactivateCNCIndicators delta = new MAINT2904_InactivateCNCIndicators();
		delta.getArchiveManager().setLoadOtherReferenceSets(true);
		delta.getArchiveManager().setRunIntegrityChecks(false);
		ArchiveImporter.setSkipSave(true);
		delta.standardExecution(args);
	}

	public void process() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			//We won't check that concepts are inactive, just incase we have some innappropriate CNC indicators to mop up
			for (Description d : c.getDescriptions())	{
				for (InactivationIndicatorEntry rm : d.getInactivationIndicatorEntries(ActiveState.ACTIVE)) {
					if (rm.isActiveSafely() && rm.getInactivationReasonId().equals(SCTID_INACT_CONCEPT_NON_CURRENT)) {
						if (SnomedUtils.isCore(rm)) {
							LOGGER.warn("Encountered core CNC indicator {}", rm);
						} else {
							//Set the concept to be clean so we don't try and output anything there
							c.setClean();
							rm.setActive(false, true);  //Force dirty
							report(c, Severity.LOW, ReportActionType.REFSET_MEMBER_INACTIVATED, d, rm);
						}
					}
				}
			}
		}
	}

}
