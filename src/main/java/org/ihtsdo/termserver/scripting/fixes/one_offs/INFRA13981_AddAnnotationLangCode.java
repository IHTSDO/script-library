package org.ihtsdo.termserver.scripting.fixes.one_offs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ComponentAnnotationEntry;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ExecutionOptions;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class INFRA13981_AddAnnotationLangCode extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(INFRA13981_AddAnnotationLangCode.class);

	protected INFRA13981_AddAnnotationLangCode(BatchFix clone) {
		super(clone);
		populateTaskDescription = true;
		reportNoChange = true;
		inputFileHasHeaderRow = true;
		additionalReportColumns = "Action Detail, Additional Detail";
	}

	public static void main(String[] args) throws TermServerScriptException {
		new INFRA13981_AddAnnotationLangCode(null).standardExecution(args, ExecutionOptions.DEFAULT);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		super.postInit();
		//standardExecution forces populateEditPanel to false; this fix relies on the edit panel being populated
		populateEditPanel = true;
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		for (ComponentAnnotationEntry a : c.getComponentAnnotationEntries()) {
			if (a.getLanguageDialectCode().isEmpty()) {
				a.setLanguageDialectCode("en");
				changesMade++;
				if (!isDryRun()) {
					updateRefsetMember(t, a, "");
				}
				report(t, c, Severity.LOW, ReportActionType.ANNOTATION_CHANGED, "Added languageDialectCode 'en' code to annotation", a.toString(true));
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> componentsToProcess = new ArrayList<>();
		for (Concept c : gl.getAllConcepts()) {
			if (c.getId().equals("1345054009")) {
				LOGGER.debug("here");
			}
			for (ComponentAnnotationEntry a : c.getComponentAnnotationEntries()) {
				if (a.getLanguageDialectCode().isEmpty()) {
					componentsToProcess.add(c);
					break;
				}
			}
		}
		LOGGER.info("Identified {} concepts to process", componentsToProcess.size());
		return componentsToProcess;
	}

}
