package org.ihtsdo.termserver.scripting.task;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.authoringservices.AuthoringServicesClient;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.JobClass;
import org.ihtsdo.termserver.scripting.TaskHelper;
import org.ihtsdo.termserver.scripting.client.TermServerClient;
import org.ihtsdo.termserver.scripting.domain.ExecutionOptions;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.Job;

import java.io.File;
import java.util.List;

public class ProjectToProjectTaskCopy extends TermServerReport implements JobClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProjectToProjectTaskCopy.class);

	private static final List<String> SKIP_TASKS = List.of("PDE-465");
	private static final List<Task.TaskStatus> TASK_STATUSES = List.of(Task.TaskStatus.IN_PROGRESS);
	private static final String FOR_USER = "ispiers";
	private static final String SKIP_WORD = "test";
	private static final String TARGET_PROJECT = "TRAIN2026";
	private static final String START_AT_TASK = "TRAIN2018-309";

	private TaskHelper taskHelper;

	public static void main(String[] args) throws TermServerScriptException {
		ExecutionOptions options = new ExecutionOptions().withNoSnapshotImport();
		new ProjectToProjectTaskCopy().standardExecution(args, options);
	}

	@Override
	public Job getJob() {
		return null;
	}

	@Override
	public void runJob() throws TermServerScriptException {
		taskHelper = new TaskHelper(this, 0, false, null);
		AuthoringServicesClient client = getAuthoringServicesClient();

		boolean skipUntilStart = START_AT_TASK != null;
		for (Task t : client.listTasksOnProject(getProject().getKey())) {
			if (skipUntilStart) {
				if (t.getKey().equals(START_AT_TASK)) {
					skipUntilStart = false;
				} else {
					LOGGER.info("Skipping task {}", t);
					continue;
				}
			}

			if (!SKIP_TASKS.contains(t.getKey())
					&& t.hasAssignee(FOR_USER)
					&& TASK_STATUSES.contains(t.getStatus())
					&& !t.getSummary().toLowerCase().contains(SKIP_WORD)) {
				transferTaskToNewProject(t);
			} else {
				LOGGER.info("Skipping task {}", t);
			}
		}
	}

	private void transferTaskToNewProject(Task t) throws TermServerScriptException {
		LOGGER.info("Copying task {}", t);
		try {
			File exportedDelta = getArchiveManager().generateDelta(t, true);
			Task newTask = taskHelper.createTask(cloneTaskInProject(t));
			if (!dryRun) {
				tsClient.importArchive(newTask.getBranchPath(), TermServerClient.ImportType.DELTA, exportedDelta);
			}
			report(PRIMARY_REPORT, t, newTask);
		} catch (TermServerScriptException e) {
			LOGGER.warn("Failed task {}", e.getMessage());
			report(PRIMARY_REPORT, t, e.getMessage());
		}
	}

	private Task cloneTaskInProject(Task t) {
		Task tClone = new Task(t);
		tClone.setProjectKey(TARGET_PROJECT);
		tClone.setKey(null);
		tClone.setBranchPath(null);
		return tClone;
	}
}
