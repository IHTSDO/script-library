package org.ihtsdo.termserver.scripting.task;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Project;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.FileUtils;
import org.ihtsdo.termserver.scripting.JobClass;
import org.ihtsdo.termserver.scripting.TaskHelper;
import org.ihtsdo.termserver.scripting.client.TermServerClient;
import org.ihtsdo.termserver.scripting.domain.ExecutionOptions;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.snapshot.TBCHelper;
import org.ihtsdo.termserver.scripting.util.RollbackBranch;
import org.ihtsdo.termserver.scripting.util.UserInteractionHelper;
import org.snomed.otf.scheduler.domain.Job;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * XDS-410 Interactively clone a task's unpromoted changes into a new task - eg when a task has fallen so far behind
 * its project that rebasing it is impractical.  The new task copies the original's summary and description, starts
 * from the project's current state, and links back to this processing report.
 */
public class CloneUnpromotedTask extends TermServerReport implements JobClass {

	private static final int PROCESSING = PRIMARY_REPORT;
	private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final String CLONED_TASK_PREFIX = "CLONED TASK: ";

	private final UserInteractionHelper ui = new UserInteractionHelper(this);

	public static void main(String[] args) throws TermServerScriptException {
		ExecutionOptions options = new ExecutionOptions().withNoSnapshotImport();
		new CloneUnpromotedTask().standardExecution(args, options);
	}

	@Override
	public Job getJob() {
		return null;
	}

	@Override
	public void postInit() throws TermServerScriptException {
		//Spare columns at the end, so an extra detail written later doesn't fail the whole run
		postInit(new String[] {"Processing"}, new String[] {"Action, Detail, , , "});
	}

	@Override
	public void runJob() throws TermServerScriptException {
		Project project = ui.confirmProject();
		report(PROCESSING, "Project confirmed", project.getKey() + " on " + project.getBranchPath());

		Task original = chooseTask(project);
		if (original == null) {
			return;
		}

		File exportedDelta = exportUnpromotedChanges(original);
		String targetProject = chooseTargetProject(project);
		Task clone = prepareClone(original, targetProject);

		if (dryRun) {
			println("Dry run - would create '" + clone.getSummary() + "' in " + targetProject + " and import the unpromoted changes of " + original.getKey());
			report(PROCESSING, "Dry run - would create task", clone.getSummary());
			return;
		}

		Task newTask = createTaskAndImport(clone, exportedDelta);
		println("Cloned " + original.getKey() + " into " + newTask.getKey() + ". Classify it before promoting, as any inferred relationships in the export came from the original's base.");
		report(PROCESSING, "Reminder", "Classify " + newTask.getKey() + " before promoting");
	}

	/**
	 * @return the task to clone, or null if it can't be found
	 */
	private Task chooseTask(Project project) throws TermServerScriptException {
		//Accept either the full key or just the task number, eg NZCS-1278 or 1278
		String response = ui.askWithDefault("Task to clone", "").toUpperCase();
		String taskKey = response.contains("-") ? response : project.getKey() + "-" + response;

		Task task;
		try {
			task = getAuthoringServicesClient().getTask(taskKey);
		} catch (RestClientException e) {
			println(taskKey + " not found: " + e.getMessage());
			report(PROCESSING, "Task not found", taskKey);
			return null;
		}

		report(PROCESSING, "Task to clone", task.getKey() + "\n" + task.getSummary() + "\nStatus: " + task.getStatus());
		report(PROCESSING, "Task branch", "State: " + task.getBranchState() +
				"\nBase: " + RollbackBranch.formatTimestamp(task.getBranchBaseTimestamp()) +
				"\nHead: " + RollbackBranch.formatTimestamp(task.getBranchHeadTimestamp()));
		if (task.getStatus() == Task.TaskStatus.PROMOTED || task.getStatus() == Task.TaskStatus.COMPLETED) {
			println("Warning: " + task.getKey() + " is " + task.getStatus() + ", so it may have no unpromoted changes to clone");
		}
		return task;
	}

	private File exportUnpromotedChanges(Task original) throws TermServerScriptException {
		File exportedDelta = new TBCHelper(this).getExportedDelta(original, true);
		report(PROCESSING, "Unpromoted changes exported", exportedDelta.getAbsolutePath());

		String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP);
		File saveAs = FileUtils.findUnusedFileOrIncrement(new File(original.getKey() + "-UnpromotedExport-" + timestamp + ".zip"));
		if (ui.askYesNo("Save a copy as " + saveAs.getName() + " in the current directory", true)) {
			try {
				Files.copy(exportedDelta.toPath(), saveAs.toPath());
				println("Saved to " + saveAs.getAbsoluteFile().getParent());
				report(PROCESSING, "Export saved", saveAs.getAbsolutePath());
			} catch (IOException e) {
				throw new TermServerScriptException("Failed to save export as " + saveAs, e);
			}
		}
		return exportedDelta;
	}

	private String chooseTargetProject(Project project) throws TermServerScriptException {
		String targetProject = ui.askYesNo("Create the new task in the same project - " + project.getKey(), true)
				? project.getKey()
				: ui.askWithDefault("Which project?", "");
		report(PROCESSING, "New task project", targetProject);
		return targetProject;
	}

	/**
	 * The clone keeps the original's summary and description, marked as a clone and linked to this report
	 */
	private Task prepareClone(Task original, String targetProject) throws TermServerScriptException {
		Task clone = new Task(original);
		clone.setProjectKey(targetProject);
		clone.setKey(null);
		clone.setBranchPath(null);
		clone.setSummary(CLONED_TASK_PREFIX + original.getSummary());
		String originalDescription = original.getDescription() == null ? "" : original.getDescription();
		clone.setDescription("<p>This task was cloned from " + original.getKey() + "</p>" +
				originalDescription +
				BatchFix.asPerProcessingReport(getReportManager().getUrl()));

		String originalAuthor = original.getAssignee() == null ? null : original.getAssignee().getUsername();
		boolean keepAuthor = originalAuthor != null && ui.askYesNo("Keep the same author - " + originalAuthor, true);
		String author = keepAuthor ? originalAuthor : ui.askWithDefault("Assign the new task to", "");
		clone.setAssignedAuthor(author);
		report(PROCESSING, "New task author", author);
		return clone;
	}

	private Task createTaskAndImport(Task clone, File exportedDelta) throws TermServerScriptException {
		Task newTask = new TaskHelper(this, 0, false, null).createTask(clone);
		println("Created " + newTask.getKey() + ": " + newTask.getSummary());
		report(PROCESSING, "New task created", newTask.getKey() + "\n" + newTask.getSummary());

		tsClient.importArchive(newTask.getBranchPath(), TermServerClient.ImportType.DELTA, exportedDelta);
		report(PROCESSING, "Unpromoted changes imported into", newTask.getKey());
		return newTask;
	}
}
