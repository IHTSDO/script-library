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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("java:S101")
public class INFRA18150_UpdateGeneNameAttribution extends BatchFix {

	private static final String GENE_STRUCTURE_ECL = "< 382391000210108 | Gene structure (cell structure) |";
	private static final String ATTRIBUTION_SCTID = "1295448001";

	private static final Logger LOGGER = LoggerFactory.getLogger(INFRA18150_UpdateGeneNameAttribution.class);

	protected INFRA18150_UpdateGeneNameAttribution(BatchFix clone) {
		super(clone);
		taskSize = 1000;
		taskPrefix = "INFRA-18150";
		reportNoChange = false;
		additionalReportColumns = "Old Value, New Value";
	}

	public static void main(String[] args) throws TermServerScriptException {
		new INFRA18150_UpdateGeneNameAttribution(null).standardExecution(args, ExecutionOptions.DEFAULT);
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		Pattern pattern = Pattern.compile("HGNC:\\s?https://www.genenames.org/data/gene-symbol-report/#!/hgnc_id/HGNC:(\\d+)");

		for (ComponentAnnotationEntry a : c.getComponentAnnotationEntries()) {
			if (ATTRIBUTION_SCTID.equals(a.getTypeId())) {
				String oldValue = a.getValue();
				Matcher matcher = pattern.matcher(oldValue);
				if (matcher.find()) {
					String newValue = "HGNC: identifiers.org/HGNC:" + matcher.group(1);
					a.setValue(newValue);
					changesMade++;
					if (!isDryRun()) {
						updateRefsetMember(t, a, "");
					}
					report(t, c, Severity.LOW, ReportActionType.ANNOTATION_CHANGED, oldValue, newValue);
				} else {
					report(t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, oldValue);
				}
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> componentsToProcess = new ArrayList<>();
		for (Concept c : findConcepts(GENE_STRUCTURE_ECL)) {
			for (ComponentAnnotationEntry a : c.getComponentAnnotationEntries()) {
				if (ATTRIBUTION_SCTID.equals(a.getTypeId())) {
					componentsToProcess.add(c);
					break;
				}
			}
		}
		LOGGER.info("Identified {} concepts to process", componentsToProcess.size());
		return componentsToProcess;
	}

}
