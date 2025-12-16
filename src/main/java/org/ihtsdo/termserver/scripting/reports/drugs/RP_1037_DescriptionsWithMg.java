package org.ihtsdo.termserver.scripting.reports.drugs;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;
import java.util.stream.Collectors;

public class RP_1037_DescriptionsWithMg extends TermServerReport implements ReportClass {


	public static void main(String[] args) throws TermServerScriptException {
		TermServerScript.run(RP_1037_DescriptionsWithMg.class, new HashMap<>(), args);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
		ReportSheetManager.setTargetFolderId(GFOLDER_ADHOC_REPORTS);
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"SCTID, FSN, SemTag, Text Matched, Descriptions, Case Significance",
				"SCTID, FSN, SemTag, Descriptions, Case Significance",
				"SCTID, FSN, SemTag, Text Matched, Descriptions, Case Significance"};
		String[] tabNames = new String[] {
				"Medicinal Product contains g/mg/ml",
				"Active CDs not listed in first tab",
				"Anything else contains g/mg"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("List descriptions with mg")
				.withDescription("")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(new JobParameters())
				.withTag(MS)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		initialiseSummaryInformation(ISSUE_COUNT);
		List<Concept> drugsReported = new ArrayList<>();
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			if (!c.isActiveSafely()) {
				continue;
			}
			for (DrugUtils.MatchingSet ms : DrugUtils.KNOWN_CASE_SENSITIVE_DRUG_UNITS) {
				checkDescriptionsForTargetText(c, ms, drugsReported);
			}
		}
		reportCDsNotPreviouslyReported(drugsReported);
	}

	private void checkDescriptionsForTargetText(Concept c, DrugUtils.MatchingSet ms, List<Concept> drugsReported) throws TermServerScriptException {
		List<Description> descriptionsContainingTargetText = c.getDescriptions(ActiveState.ACTIVE)
				.stream()
				.filter(d -> DrugUtils.containsTargetText(d, ms))
				.toList();
		if (!descriptionsContainingTargetText.isEmpty()) {
			String descriptions = descriptionsContainingTargetText.stream()
					.map(Description::getTerm)
					.collect(Collectors.joining(",\n"));
			String caseSignificances = descriptionsContainingTargetText.stream()
					.map(d -> SnomedUtils.translateCaseSignificanceFromEnumSafely(d.getCaseSignificance()))
					.collect(Collectors.joining(",\n"));
			if (isMedicinalProduct(c)) {
				report(PRIMARY_REPORT, c, ms.getTargetText(), descriptions, caseSignificances);
				drugsReported.add(c);
			} else {
				report(TERTIARY_REPORT, c, ms.getTargetText(), descriptions, caseSignificances);
			}
			countIssue(c);
		}
	}

	private void reportCDsNotPreviouslyReported(List<Concept> drugsReported) throws TermServerScriptException {
		List<Concept> allCDs = gl.getAllConcepts().stream()
				.filter(Component::isActiveSafely)
				.filter(c -> c.getFsn().contains("(clinical drug)"))
				.toList();
		List<Concept> notReported = allCDs.stream()
				.filter(c -> !drugsReported.contains(c))
				.toList();
		for (Concept c : notReported) {
			report(SECONDARY_REPORT, c, SnomedUtils.getDescriptionsFull(c));
			countIssue(c);
		}
	}

	private boolean isMedicinalProduct(Concept c) {
		try {
			return c.getAncestors(RF2Constants.NOT_SET).contains(MEDICINAL_PRODUCT);
		} catch (TermServerScriptException e) {
			throw new IllegalStateException(e);
		}
	}

}
