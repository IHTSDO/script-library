package org.ihtsdo.termserver.scripting.task;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Classification;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.TaskUser;
import org.ihtsdo.otf.utils.FileUtils;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.JobClass;
import org.ihtsdo.termserver.scripting.domain.Branch;
import org.ihtsdo.termserver.scripting.domain.ExecutionOptions;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.snapshot.TBCHelper;
import org.ihtsdo.termserver.scripting.util.MultiArchiveImporter;
import org.ihtsdo.termserver.scripting.util.RollbackBranch;
import org.snomed.otf.scheduler.domain.Job;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * XDS-412 Interactive support for rolling a project branch back.  Establishes the current state of play -
 * optionally exporting the project's unpromoted changes and listing all of its tasks - then rolls the project branch
 * back one commit at a time, naming each commit from the promoted task it came from where possible.
 * Never rolls back beyond the later of the branch's base and creation time.
 */
public class RollbackProjectCommits extends TermServerReport implements JobClass {

	private static final int PROCESSING = PRIMARY_REPORT;
	private static final int TASKS = SECONDARY_REPORT;
	private static final int ROLLBACK = TERTIARY_REPORT;
	private static final int REIMPORT = QUATERNARY_REPORT;
	private static final String BEHIND = "BEHIND";
	private static final String UP_TO_DATE = "UP_TO_DATE";
	private static final String YES_NO_DEFAULT_YES = "? Y/N [Y]: ";
	private static final String REIMPORT_SKIPPED = "Re-import skipped";
	private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final DateTimeFormatter CONSOLE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

	private List<Task> projectTasks = new ArrayList<>();
	private final List<Task> tasksRolledBack = new ArrayList<>();
	private File savedExport = null;

	public static void main(String[] args) throws TermServerScriptException {
		ExecutionOptions options = new ExecutionOptions().withNoSnapshotImport();
		new RollbackProjectCommits().standardExecution(args, options);
	}

	@Override
	public Job getJob() {
		return null;
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] tabNames = new String[] {
				"Processing",
				"Tasks",
				"Rollback",
				"Re-import"
		};
		//Spare columns at the end of each tab, so an extra detail written later doesn't fail the whole run
		String[] columnHeadings = new String[] {
				"Action, Detail, , , ",
				"Key, Summary, Status, Assignee, Reviewers, BranchState, BaseTimestamp, HeadTimestamp, Created, Updated, Classification, Classification Last Commit, Validation, Feedback, , , ",
				"Branch, Commit Rolled Back, New Head, New Base, State, Detail, , , ",
				"Task, Archive, User, Result, , , "
		};
		postInit(tabNames, columnHeadings);
	}

	@Override
	public void runJob() throws TermServerScriptException {
		confirmProject();
		exportUnpromotedChanges();
		reportTasks();
		rollbackCommits();
		offerTaskDeletion();
		offerReimport();
	}

	private void confirmProject() throws TermServerScriptException {
		boolean useCurrentProject = false;
		if (!StringUtils.isEmpty(projectName)) {
			print("Use current project - " + projectName + YES_NO_DEFAULT_YES);
			useCurrentProject = !STDIN.nextLine().trim().equalsIgnoreCase("N");
		}

		if (!useCurrentProject) {
			print("Which project? : ");
			projectName = STDIN.nextLine().trim();
			recoverProjectFromProjectName(projectName);
		}
		report(PROCESSING, "Project confirmed", getProject().getKey() + " on " + getProject().getBranchPath());
	}

	private void exportUnpromotedChanges() throws TermServerScriptException {
		print("Export unpromoted changes from " + getProject().getKey() + YES_NO_DEFAULT_YES);
		if (STDIN.nextLine().trim().equalsIgnoreCase("N")) {
			report(PROCESSING, "Unpromoted export skipped", "User declined");
			return;
		}

		File exportedDelta = new TBCHelper(this).getExportedDelta(getProject(), true);
		report(PROCESSING, "Unpromoted changes exported", exportedDelta.getAbsolutePath());

		String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP);
		File saveAs = FileUtils.findUnusedFileOrIncrement(new File(getProject().getKey() + "-UnpromotedExport-" + timestamp + ".zip"));
		print("Save as " + saveAs.getName() + " in the current directory" + YES_NO_DEFAULT_YES);
		if (STDIN.nextLine().trim().equalsIgnoreCase("N")) {
			report(PROCESSING, "Unpromoted export not saved", "Temporary file is deleted on exit: " + exportedDelta.getAbsolutePath());
			return;
		}

		try {
			Files.copy(exportedDelta.toPath(), saveAs.toPath());
			savedExport = saveAs;
			println("Saved to " + saveAs.getAbsoluteFile().getParent());
			report(PROCESSING, "Unpromoted export saved", saveAs.getAbsolutePath());
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to save unpromoted export as " + saveAs, e);
		}
	}

	private void reportTasks() throws TermServerScriptException {
		try {
			projectTasks = getAuthoringServicesClient().listAllTasksOnProject(getProject().getKey());
		} catch (RestClientException e) {
			throw new TermServerScriptException("Failed to list tasks on " + getProject().getKey(), e);
		}
		report(PROCESSING, "Tasks recovered", projectTasks.size() + " tasks on " + getProject().getKey());

		for (Task t : projectTasks) {
			Classification classification = t.getLatestClassificationJson();
			report(TASKS,
					t.getKey(),
					t.getSummary(),
					t.getStatus(),
					t.getAssignee() == null ? "" : t.getAssignee().getUsername(),
					t.getReviewers() == null ? "" : t.getReviewers().stream().map(TaskUser::getUsername).collect(Collectors.joining(", ")),
					t.getBranchState(),
					formatTimestamp(t.getBranchBaseTimestamp()),
					formatTimestamp(t.getBranchHeadTimestamp()),
					t.getCreated(),
					t.getUpdated(),
					classification == null ? "" : classification.getStatus(),
					classification == null ? "" : classification.getLastCommitDate(),
					t.getLatestValidationStatus(),
					t.getFeedbackMessagesStatus());
		}
	}

	private void rollbackCommits() throws TermServerScriptException {
		Branch branch = tsClient.getBranch(getProject().getBranchPath());
		println("\n" + describeForConsole("Current", branch));
		report(PROCESSING, getProject().getKey() + " Branch before rollback", RollbackBranch.describeState(branch, "\n"));

		//The main aim is to reach BEHIND: the branch then holds nothing that isn't also on its parent, so it's
		//clean and ready to be brought up to date with a rebase.  As a backstop, going back in time we also stop at
		//whichever we reach first of the base (moved forward by each rebase) or the point the branch was created.
		long base = requireTimestamp(branch.getBaseTimestamp(), "base", branch);
		Long creation = branch.getCreationTimestamp();
		long stopAt = creation == null ? base : Math.max(base, creation);
		println("Rollback will stop when the branch is BEHIND its parent, or at " + formatForConsole(stopAt) + " (the later of the base and creation time)");

		while (branch != null) {
			branch = rollbackNextCommit(branch, stopAt);
		}
	}

	/**
	 * @return the branch after rolling back its head commit, or null if we've stopped
	 */
	private Branch rollbackNextCommit(Branch branch, long stopAt) throws TermServerScriptException {
		String stopReason = getStopReason(branch, stopAt);
		if (stopReason != null) {
			println(stopReason);
			reportRollback(branch, "", stopReason);
			return null;
		}

		long head = requireHead(branch);
		List<Task> promotedHere = tasksPromotedAt(head);
		String commitLabel = describeCommit(promotedHere);
		println("\nNext commit to roll back: " + formatForConsole(head) + " - " + commitLabel);

		if (dryRun) {
			String msg = "Dry run - would roll back " + commitLabel + ". Stopping, as without a real rollback there is no new head to look at";
			println(msg);
			reportRollback(branch, "", msg);
			return null;
		}

		if (askRollbackOrQuit().equals("Q")) {
			reportRollback(branch, "", "Stopped by user");
			return null;
		}
		return rollbackHeadCommit(branch, promotedHere, commitLabel);
	}

	private String getStopReason(Branch branch, long stopAt) throws TermServerScriptException {
		String projectKey = getProject().getKey();
		if (BEHIND.equals(branch.getState())) {
			return "Hard stop: " + projectKey + " is BEHIND its parent - nothing left on the branch that isn't also on the parent, so it's ready to rebase";
		}
		if (UP_TO_DATE.equals(branch.getState())) {
			return projectKey + " is UP_TO_DATE - no commits of its own since its last rebase, so nothing to roll back";
		}
		if (requireHead(branch) <= stopAt) {
			return "Hard stop: head has reached " + formatForConsole(stopAt) + " (the later of the base and creation time)";
		}
		return null;
	}

	private String askRollbackOrQuit() {
		String choice = "";
		while (!choice.equals("R") && !choice.equals("Q")) {
			print("Rollback (R) or Quit (Q): ");
			choice = STDIN.nextLine().trim().toUpperCase();
		}
		return choice;
	}

	private Branch rollbackHeadCommit(Branch branch, List<Task> promotedHere, String commitLabel) throws TermServerScriptException {
		long head = requireHead(branch);
		tsClient.adminRollbackCommit(branch);
		Branch after = tsClient.getBranch(getProject().getBranchPath());
		println("Rolled back " + commitLabel + ".\n" + describeForConsole("New", after));
		reportRollback(after, formatTimestamp(head), "Rolled back " + commitLabel);
		getReportManager().flushFiles(false);
		tasksRolledBack.addAll(promotedHere);

		if (requireHead(after) >= head) {
			throw new TermServerScriptException("Rollback did not move the head of " + getProject().getBranchPath() + " back from " + formatTimestamp(head));
		}
		//Once all the project's own commits are gone, we expect it to show as BEHIND its parent
		if (!Objects.equals(branch.getState(), after.getState())) {
			String msg = "Branch state changed from " + branch.getState() + " to " + after.getState();
			println(msg);
			report(PROCESSING, getProject().getKey() + " Branch state changed", msg);
		}
		return after;
	}

	private long requireHead(Branch branch) throws TermServerScriptException {
		return requireTimestamp(branch.getHeadTimestamp(), "head", branch);
	}

	//Snowstorm should always supply these, but fail clearly rather than with a NullPointerException if it doesn't
	private long requireTimestamp(Long timestamp, String which, Branch branch) throws TermServerScriptException {
		if (timestamp == null) {
			throw new TermServerScriptException("No " + which + " timestamp returned for branch " + branch.getPath());
		}
		return timestamp;
	}

	//Columns: Branch, Commit Rolled Back, New Head, New Base, State, Detail
	private void reportRollback(Branch branch, String commitRolledBack, String detail) throws TermServerScriptException {
		report(ROLLBACK, getProject().getBranchPath(), commitRolledBack, formatTimestamp(branch.getHeadTimestamp()),
				formatTimestamp(branch.getBaseTimestamp()), branch.getState(), detail);
	}

	//A promoted task's branch ends up with both head and base at the timestamp of its promotion commit on the project
	private List<Task> tasksPromotedAt(long commitTimestamp) {
		return projectTasks.stream()
				.filter(t -> t.getStatus() == Task.TaskStatus.PROMOTED || t.getStatus() == Task.TaskStatus.COMPLETED)
				.filter(t -> Long.valueOf(commitTimestamp).equals(t.getBranchHeadTimestamp())
						&& Long.valueOf(commitTimestamp).equals(t.getBranchBaseTimestamp()))
				.toList();
	}

	private String describeCommit(List<Task> promotedHere) {
		if (promotedHere.isEmpty()) {
			//Other commits on a project: a rebase (including changes saved via the merge screen) or a saved classification
			return "no matching promoted task (rebase or saved classification?)";
		}
		return "promotion of " + promotedHere.stream()
				.map(t -> t.getKey() + " (" + t.getSummary() + ")")
				.collect(Collectors.joining(", "));
	}

	private void offerTaskDeletion() throws TermServerScriptException {
		if (tasksRolledBack.isEmpty()) {
			return;
		}
		String taskKeys = tasksRolledBack.stream().map(Task::getKey).collect(Collectors.joining(", "));
		print("Delete " + tasksRolledBack.size() + " tasks on " + getProject().getKey() + " (" + taskKeys + ")" + YES_NO_DEFAULT_YES);
		if (STDIN.nextLine().trim().equalsIgnoreCase("N")) {
			report(PROCESSING, "Task deletion skipped", taskKeys);
			return;
		}

		for (Task t : tasksRolledBack) {
			try {
				getAuthoringServicesClient().deleteTask(t, false);
				report(PROCESSING, "Task deleted", t.getKey());
			} catch (RestClientException e) {
				report(PROCESSING, "Task deletion failed", t.getKey() + ": " + e.getMessage());
			}
		}
	}

	/**
	 * Now the branch is clean, the export taken at the start can be loaded back into a single new task, saving
	 * the authors from recreating that work.  The project must be rebased first (done by the user in the UI) so
	 * the re-imported content sits on top of the parent's current state.
	 */
	private void offerReimport() throws TermServerScriptException {
		File archive = chooseArchiveToReimport();
		if (archive == null) {
			return;
		}

		if (dryRun) {
			println("Dry run - would re-import " + archive.getName() + " into a new task on " + getProject().getKey());
			report(PROCESSING, "Dry run - would re-import", archive.getAbsolutePath());
			return;
		}

		if (confirmRebased()) {
			reimport(archive);
		}
	}

	/**
	 * Ask whether to re-import, then which file - pressing return accepts the default in square brackets: the
	 * export saved this run, otherwise the latest export for this project in the current directory.
	 * @return the file to re-import, or null if the user doesn't want to re-import
	 */
	private File chooseArchiveToReimport() throws TermServerScriptException {
		String projectKey = getProject().getKey();
		print("Re-import an unpromoted export into a new task on " + projectKey + YES_NO_DEFAULT_YES);
		if (STDIN.nextLine().trim().equalsIgnoreCase("N")) {
			report(PROCESSING, REIMPORT_SKIPPED, "User declined");
			return null;
		}

		File defaultArchive = savedExport != null ? savedExport : findLatestExport(projectKey);
		String defaultName = defaultArchive == null ? "" : defaultArchive.getName();
		print("File to re-import [" + defaultName + "]: ");
		String response = STDIN.nextLine().trim();
		File archive = response.isEmpty() ? defaultArchive : new File(response);
		if (archive == null) {
			report(PROCESSING, REIMPORT_SKIPPED, "No file given");
		} else if (!archive.isFile()) {
			println("File not found: " + archive.getAbsolutePath());
			report(PROCESSING, REIMPORT_SKIPPED, "File not found: " + archive.getAbsolutePath());
			archive = null;
		}
		return archive;
	}

	private boolean confirmRebased() throws TermServerScriptException {
		String projectKey = getProject().getKey();
		//UP_TO_DATE means level with the parent, whatever its rebase history, so it's ready to receive the re-import
		Branch current = tsClient.getBranch(getProject().getBranchPath());
		if (UP_TO_DATE.equals(current.getState())) {
			println(projectKey + " is UP_TO_DATE with its parent, so ready to receive the re-import");
			return true;
		}

		print("Has " + projectKey + " been rebased in the authoring UI? Y/N [N]: ");
		if (!STDIN.nextLine().trim().equalsIgnoreCase("Y")) {
			println("Please rebase " + projectKey + " first, so the re-import sits on top of its parent's current content");
			report(PROCESSING, REIMPORT_SKIPPED, projectKey + " not yet rebased");
			return false;
		}
		Branch branch = tsClient.getBranch(getProject().getBranchPath());
		if (BEHIND.equals(branch.getState())) {
			println(projectKey + " still shows as BEHIND its parent - please complete the rebase before re-importing");
			report(PROCESSING, REIMPORT_SKIPPED, projectKey + " still BEHIND after confirmed rebase");
			return false;
		}
		return true;
	}

	private void reimport(File archive) throws TermServerScriptException {
		String replacedTasks = tasksRolledBack.stream().map(Task::getKey).collect(Collectors.joining(", "));
		if (replacedTasks.isEmpty()) {
			print("Which tasks does this re-import replace? (optional, comma separated): ");
			replacedTasks = STDIN.nextLine().trim();
		}
		print("Which ticket is this work for? (optional, used as the task summary prefix): ");
		String ticket = STDIN.nextLine().trim();

		MultiArchiveImporter importer = new MultiArchiveImporter(this);
		importer.setMode(MultiArchiveImporter.MODE.ALL_ARCHIVES_IN_ONE_TASK);
		importer.setReportTabIdx(REIMPORT);
		String summary = "Re-import of " + (replacedTasks.isEmpty() ? archive.getName() : replacedTasks);
		importer.setTaskSummary(ticket.isEmpty() ? summary : ticket + " " + summary);
		importer.setTaskNotes("Re-import of unpromoted changes saved in " + archive.getName() +
				(replacedTasks.isEmpty() ? "" : ", replacing " + replacedTasks) + ", after the project was rolled back and rebased.");
		importer.promptForAuthor();
		importer.importArchive(archive);

		Task newTask = importer.getLastTaskCreated();
		String newTaskKey = newTask == null ? "unknown" : newTask.getKey();
		println("Re-imported " + archive.getName() + " into " + newTaskKey);
		report(PROCESSING, "Re-imported file", archive.getAbsolutePath());
		report(PROCESSING, "Re-imported into task", newTaskKey);
	}

	private File findLatestExport(String projectKey) {
		File[] exports = new File(".").listFiles((dir, name) -> name.startsWith(projectKey + "-UnpromotedExport-") && name.endsWith(".zip"));
		if (exports == null || exports.length == 0) {
			return null;
		}
		return Arrays.stream(exports).max(Comparator.comparingLong(File::lastModified)).orElse(null);
	}

	//On screen, the state comes first as it matters most, and times are to the second without the epoch value
	private String describeForConsole(String prefix, Branch branch) {
		return prefix + " " + getProject().getKey() + " State - " + branch.getState() + ". " +
				"Head: " + formatForConsole(branch.getHeadTimestamp()) +
				" Base: " + formatForConsole(branch.getBaseTimestamp()) +
				" Created: " + formatForConsole(branch.getCreationTimestamp());
	}

	private String formatForConsole(Long timestamp) {
		return timestamp == null ? "unknown" : CONSOLE_TIME.format(Instant.ofEpochMilli(timestamp));
	}

	//For the record: UTC time first for people to read, with the epoch milliseconds alongside
	private String formatTimestamp(Long timestamp) {
		return timestamp == null ? "" : RollbackBranch.formatTimestamp(timestamp);
	}
}
