package org.ihtsdo.termserver.scripting.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.MergeReview;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.JobClass;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ExecutionOptions;
import org.ihtsdo.termserver.scripting.domain.MergeReviewConceptVersions;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.Job;
import org.springframework.core.ParameterizedTypeReference;

import java.util.*;

public class MergeConflictResolution extends TermServerReport implements JobClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(MergeConflictResolution.class);
	private static final String MERGE_REVIEW_ID = "84989d31-1e3b-4ec4-9dfa-a994ddab03a2";

	public static void main(String[] args) throws TermServerScriptException {
		ExecutionOptions options = new ExecutionOptions().withNoSnapshotImport();
		new MergeConflictResolution().standardExecution(args, options);
	}

	@Override
	public Job getJob() {
		return null;
	}

	@Override
	public void runJob() throws TermServerScriptException {
		Task projectAsTask;
		try {
			projectAsTask = new Task(null, null, null);
			projectAsTask.setKey(getProject().getKey());
			projectAsTask.setBranchPath(getProject().getBranchPath());
			//Recover all active tasks from project and mark as deleted
			MergeReview mergeReview = getTSClient().waitForMergeReviewToComplete(MERGE_REVIEW_ID);
			ObjectMapper mapper = new ObjectMapper();
			String json = mapper.writeValueAsString(mergeReview);

			//We're expecting conflicts, or there's no point in running.
			if (mergeReview.getStatus().equals(MergeReview.Status.CURRENT)) {
				LOGGER.info("Merge review found {}", json);
			} else {
				LOGGER.info("Invalid merge review state {}", json);
			}

			//Now recover the details
			String mergeConflictDetailsUrl = getTSClient().getMergeReviewUrl(MERGE_REVIEW_ID) + "/details";
			ParameterizedTypeReference<Collection<MergeReviewConceptVersions>> typeRef = new ParameterizedTypeReference<>() {};

			Collection<MergeReviewConceptVersions> conflictVersion = getTSClient().getObjectCollection(mergeConflictDetailsUrl, typeRef);
			List<String> savedConceptIds = new ArrayList<>();
			for (MergeReviewConceptVersions cv : conflictVersion) {
				getTSClient().saveMergeReviewAcceptedConcept(MERGE_REVIEW_ID, cv.getTargetConcept());
				savedConceptIds.add(cv.getTargetConcept().getConceptId());
				report(PRIMARY_REPORT, cv.getTargetConcept());
			}
			//Finally we can apply the merge with the concepts chosen
			getTSClient().applyMerge(MERGE_REVIEW_ID);
		} catch (JsonProcessingException e) {
			throw new TermServerScriptException(e);
		}
	}
}
