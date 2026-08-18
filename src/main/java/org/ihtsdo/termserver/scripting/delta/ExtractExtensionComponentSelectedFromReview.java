package org.ihtsdo.termserver.scripting.delta;

import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ReviewedConceptsList;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtractExtensionComponentSelectedFromReview extends ExtractExtensionComponents {

	private static final Logger LOGGER = LoggerFactory.getLogger(ExtractExtensionComponentSelectedFromReview.class);

	private static final String CATEGORY_CONCEPTS_PER_TASK = "Concepts identified per task";

	private static final List<String> TASKS_WITH_REVIEWS = List.of(
			"HQ-21",
			"HQ-28",
			"HQ-29",
			"HQ-32",
			"HQ-33",
			"HQ-38",
			"HQ-46",
			"HQ-30",
			"HQ-36",
			"HQ-34",
			"HQ-55"
	);

	public ExtractExtensionComponentSelectedFromReview() {
		this.selectViaReview = true;
	}

	public static void main(String[] args) throws TermServerScriptException {
		new ExtractExtensionComponentSelectedFromReview().doExtensionComponentExtraction(args);
	}

	@Override
	public List<Component> getConceptsInReview() throws TermServerScriptException {
		List<Component> conceptsInReview = new ArrayList<>();
		for (String taskKey : TASKS_WITH_REVIEWS) {
			initialiseSummaryCount(CATEGORY_CONCEPTS_PER_TASK, taskKey);
			ReviewedConceptsList reviewedConcepts;
			try {
				reviewedConcepts = getAuthoringServicesClient().getReviewedConcepts(taskKey);
			} catch (RestClientException e) {
				throw new TermServerScriptException("Unable to recover reviewed concepts for task " + taskKey, e);
			}
			LOGGER.info("{} reviewed concepts recovered from {} (approved {})", reviewedConcepts.getConceptIds().size(), taskKey, reviewedConcepts.getApprovalDate());
			for (String conceptId : reviewedConcepts.getConceptIds()) {
				Concept c = gl.getConceptSafely(conceptId);
				conceptOriginTask.put(c, taskKey);
				conceptsInReview.add(c);
				incrementSummaryCount(CATEGORY_CONCEPTS_PER_TASK, taskKey);
			}
		}
		return conceptsInReview;
	}

	@Override
	protected void extractComponent(Component thisComponent, List<Component> componentsToProcess, boolean doAdditionalProcessing) throws TermServerScriptException {
		Concept concept = (Concept) thisComponent;
		String originTask = conceptOriginTask.get(concept);
		report(concept, Severity.LOW, ReportActionType.INFO, "Concept selected via review from task " + originTask);
	}
}
