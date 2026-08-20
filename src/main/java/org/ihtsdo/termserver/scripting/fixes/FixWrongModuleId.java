package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;


/*
INFRA-6803 Fix fallout from transfer from other module
*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FixWrongModuleId extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(FixWrongModuleId.class);

	String findModule = "1145237009";
	String replaceModule = "900000000000207008";
	
	protected FixWrongModuleId(BatchFix clone) {
		super(clone);
		this.reportNoChange = false;  //Might just be langrefset which we'll modify directly
		this.populateTaskDescription = false;
		this.additionalReportColumns = "Active, Details";
	}

	public static void main(String[] args) throws TermServerScriptException {
		new FixWrongModuleId(null).standardExecution(args, ExecutionOptions.DEFAULT);
	}

	@Override
	protected void init(String[] args) throws TermServerScriptException {
		//standardExecution() forces runStandAlone false before init() resolves the project
		//this fix needs it true, and postInit() runs too late to affect that resolution
		runStandAlone = true;
		super.init(args);
	}

	@Override
	public int doFix(Task task, Concept c, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(c, task.getBranchPath());
		int changesMade = sortModule(task, c, loadedConcept);
		if (changesMade > 0) {
			try {
				updateConcept(task, loadedConcept, "");
			} catch (Exception e) {
				report(task, c, Severity.CRITICAL, ReportActionType.API_ERROR, c.isActive(), "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
			}
		}
		return changesMade;
	}

	private int sortModule(Task t, Concept localLoad, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		
		if (c.getId().equals("373120008")) {
			LOGGER.debug("here");
		}
		
		if (c.getModuleId().contentEquals(findModule)) {
			c.setModuleId(replaceModule);
			report(t, c, Severity.MEDIUM, ReportActionType.MODULE_CHANGE_MADE, "Concept module set to " + replaceModule);
			changesMade++;
		}
		
		for (Description d : localLoad.getDescriptions()) {
			Description dTS = c.getDescription(d.getId());
			if (dTS.getModuleId().contentEquals(findModule)) {
				dTS.setModuleId(replaceModule);
				report(t, c, Severity.MEDIUM, ReportActionType.MODULE_CHANGE_MADE, "Description module set to " + replaceModule, dTS);
				changesMade++;
			}
			//To get the langrefset ids, we need to work with the locally loaded description
			for (LangRefsetEntry l : d.getLangRefsetEntries()) {
				if (l.getModuleId().contentEquals(findModule)) {
					l.setModuleId(replaceModule);
					report(t, c, Severity.MEDIUM, ReportActionType.MODULE_CHANGE_MADE, "LangRefset module set to " + replaceModule, l);
					updateRefsetMember(t, l, "");
				}
			}
		}
		
		for (Axiom a : c.getClassAxioms()) {
			if (a.getModuleId().contentEquals(findModule)) {
				a.setModuleId(replaceModule);
				report(t, c, Severity.MEDIUM, ReportActionType.MODULE_CHANGE_MADE, "Axiom module set to " + replaceModule, a);
				changesMade++;
			}
		}
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		Set<Concept> allAffected = new TreeSet<Concept>();  //We want to process in the same order each time, in case we restart and skip some.
		nextConcept:
		for (Concept c : gl.getAllConcepts()) {
			if (c.getModuleId().contentEquals(findModule)) {
				allAffected.add(c);
				continue nextConcept;
			}
			
			for (Description d : c.getDescriptions()) {
				if (d.getModuleId().contentEquals(findModule)) {
					allAffected.add(c);
					continue nextConcept;
				}
				for (LangRefsetEntry l : d.getLangRefsetEntries()) {
					if (l.getModuleId().contentEquals(findModule)) {
						allAffected.add(c);
						continue nextConcept;
					}
				}
			}
			
			for (AxiomEntry a : c.getAxiomEntries()) {
				if (a.getModuleId().contentEquals(findModule)) {
					allAffected.add(c);
					continue nextConcept;
				}
			}
		}
		return new ArrayList<Component>(allAffected);
	}
	
}
