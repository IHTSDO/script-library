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

@SuppressWarnings("java:S110")
public class ExtractComponentsSelectedFromReview extends ExtractExtensionComponents {

	private static final Logger LOGGER = LoggerFactory.getLogger(ExtractComponentsSelectedFromReview.class);

	private static final String CATEGORY_CONCEPTS_INCLUDED_PER_TASK = "Concepts identified for inclusion per task";
	private static final String CATEGORY_CONCEPTS_EXCLUDED_PER_TASK = "Concepts identified for exclusion per task";

	private static final List<String> TASKS_WITH_REVIEWS = List.of(
			"HQ-21",
			"HQ-28",
			"HQ-29",
			"HQ-32",
			"HQ-33",
			"HQ-38",
			"HQ-46"
	);

	private static final List<String> EXCLUDE_REVIEWED_IN_TASK = List.of(
			"HQ-30",
			"HQ-36",
			"HQ-34",
			"HQ-55"
	);

	private static final List<String> ADD_CONCEPTS = List.of("1380193004 |Mesenchymal neoplasm (morphologic abnormality)|",
			"381781000210103 |Malignant cauda equina neuroendocrine tumor (morphologic abnormality)|");

	private static final List<String> REMOVE_CONCEPTS = List.of("1287148009 |Histologic grade of retinoblastoma of eye (observable entity)|",
			"1351752003 |Mesenchymal neoplasm (morphologic abnormality)|",
			"1344936003 |Malignant cauda equina neuroendocrine tumor (morphologic abnormality)|");

	public ExtractComponentsSelectedFromReview() {
		this.selectViaReview = true;
	}

	public static void main(String[] args) throws TermServerScriptException {
		new ExtractComponentsSelectedFromReview().doComponentExtraction(args);
	}

	@Override
	public List<Component> getConceptsInReview() throws TermServerScriptException {
		List<Component> exclusions = getConceptsInReview(CATEGORY_CONCEPTS_EXCLUDED_PER_TASK, EXCLUDE_REVIEWED_IN_TASK);
		List<Component> candidates = getConceptsInReview(CATEGORY_CONCEPTS_INCLUDED_PER_TASK, TASKS_WITH_REVIEWS);
		List<Component> inclusions = new ArrayList<>();

		for (Component component : candidates) {
			Concept c = (Concept) component;
			if (exclusions.contains(c)) {
				report(c, Severity.LOW, ReportActionType.INFO, "Excluded due to conflicting source tasks", String.join(", ", conceptOriginTask.get(component)));
			} else {
				inclusions.add(c);
			}
		}

		for (String conceptStr : ADD_CONCEPTS) {
			Concept c = gl.getConceptSafely(conceptStr);
			inclusions.add(c);
			report(c, Severity.LOW, ReportActionType.INFO, "Concept manually added", "Manually specified");
		}

		for (String conceptStr : REMOVE_CONCEPTS) {
			Concept c = gl.getConceptSafely(conceptStr);
			inclusions.remove(c);
			report(c, Severity.LOW, ReportActionType.INFO, "Concept manually removed", "Manually specified");
		}

		return inclusions;
	}


	public List<Component> getConceptsInReview(String categoryStr, List<String> tasks) throws TermServerScriptException {
		List<Component> conceptsInReview = new ArrayList<>();

		for (String taskKey : tasks) {
			initialiseSummaryCount(categoryStr, taskKey);
			ReviewedConceptsList reviewedConcepts;
			try {
				reviewedConcepts = getAuthoringServicesClient().getReviewedConcepts(taskKey);
			} catch (RestClientException e) {
				throw new TermServerScriptException("Unable to recover reviewed concepts for task " + taskKey, e);
			}
			LOGGER.info("{} reviewed concepts recovered from {} (approved {})", reviewedConcepts.getConceptIds().size(), taskKey, reviewedConcepts.getApprovalDate());
			for (String conceptId : reviewedConcepts.getConceptIds()) {
				Concept c = gl.getConceptSafely(conceptId);
				conceptOriginTask.computeIfAbsent(c, k -> new ArrayList<>()).add(taskKey);
				conceptsInReview.add(c);
				incrementSummaryCount(categoryStr, taskKey);
			}
		}
		return conceptsInReview;
	}

}
