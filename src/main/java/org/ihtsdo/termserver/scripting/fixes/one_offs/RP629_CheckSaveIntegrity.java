package org.ihtsdo.termserver.scripting.fixes.one_offs;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Save a concept that has description inactivation indicator and association
 */
public class RP629_CheckSaveIntegrity extends BatchFix {
	
	private static Logger LOGGER = LoggerFactory.getLogger(RP629_CheckSaveIntegrity.class);
	
	protected RP629_CheckSaveIntegrity(BatchFix clone) {
		super(clone);
		reportNoChange = true;
		populateTaskDescription = false;
		additionalReportColumns = "Action Detail";
	}

	public static void main(String[] args) throws TermServerScriptException {
		new RP629_CheckSaveIntegrity(null).standardExecution(args, ExecutionOptions.DEFAULT);
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		LOGGER.info("Imported View");
		outputInactiveDescriptions(c);
		
		LOGGER.info("Loaded View");
		Concept loadedConcept = loadConcept(c, t.getBranchPath());
		outputInactiveDescriptions(loadedConcept);
		updateConcept(t, loadedConcept, info);
		
		LOGGER.info("Saved View");
		loadedConcept = loadConcept(c, t.getBranchPath());
		outputInactiveDescriptions(loadedConcept);
		return changesMade;
	}

	private void outputInactiveDescriptions(Concept c) throws TermServerScriptException {
		for (Description d : c.getDescriptions(ActiveState.INACTIVE)) {
			LOGGER.debug(d + " : " + d.getInactivationIndicator());
			LOGGER.debug("   " + SnomedUtils.prettyPrintHistoricalAssociations(d, gl));
		}
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> components = new ArrayList<>();
		//components.add(gl.getConcept("90534006"));
		components.add(gl.getConcept("418249008"));
		return components;
	}

}
