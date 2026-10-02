package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.FileUtils;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.util.MultiArchiveImporter;
import org.ihtsdo.termserver.scripting.util.UserInteractionHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DeltaGeneratorWithAutoImport extends DeltaGenerator {

	protected String taskPrefix;
	protected MultiArchiveImporter importer;
	protected final UserInteractionHelper ui = new UserInteractionHelper(this);
	private File archive;
	private final List<File> archivesCreatedDuringThisRun = new ArrayList<>();

	protected void addArchiveCreated(File archive) {
		archivesCreatedDuringThisRun.add(archive);
	}

	protected List<File> getArchivesCreatedDuringThisRun() {
		return archivesCreatedDuringThisRun;
	}

	protected void importArchiveToTask(File archive) throws TermServerScriptException {
		this.archive = archive;
		importer = new MultiArchiveImporter(this);
		boolean proceed = reviewSettingsWithUser();

		if (proceed) {
			importer.setTaskPrefix(taskPrefix);
			importer.importArchive(this.archive);
		}
	}

	private boolean reviewSettingsWithUser() throws TermServerScriptException {
		if (!checkProceed()) {
			return false;
		}

		promptForTaskPrefixIfNeeded();
		reviewArchiveNaming();
		reviewEnvironmentAndProject();
		reviewExistingTaskOption();
		reviewAuthor();

		return ui.askYesNo("Ready to import into a task in " + projectName, true);
	}

	protected boolean checkProceed() {
		//Let's output the processing report so the user can review it before making decisions
		println("Processing Report: " + getReportManager().getUrl());

		return ui.askYesNo("Do you want to proceed with auto-import", true);
	}

	protected void promptForTaskPrefixIfNeeded() {
		//Quite often forget to set a task prefix, so let's prompt for it
		if (StringUtils.isEmpty(taskPrefix)) {
			taskPrefix = ui.askOptional("What INFRA/MSSP/XDS ticket are you working here");
		}
	}

	private void reviewArchiveNaming() throws TermServerScriptException {
		//Check if we're going to rename the file to be the task prefix, without overwriting a previous run's archive
		File renamed = FileUtils.findUnusedFileOrIncrement(new File(archive.getParentFile(), taskPrefix + ".zip"));
		if (ui.askYesNo("Rename " + archive.getName() + " to " + renamed.getName(), true)) {
			File oldFile = archive;
			archive = renamed;
			if (!oldFile.renameTo(archive)) {
				throw new TermServerScriptException("Failed to rename " + oldFile + " to " + archive);
			}
		}
	}

	protected void reviewEnvironmentAndProject() throws TermServerScriptException {
		if (!ui.askYesNo("Import into same environment", true)) {
			determineEnvironment(true);
			initialiseSnomedServiceClients();
		}

		boolean useCurrentProject = !StringUtils.isEmpty(projectName)
				&& ui.askYesNo("Use current project - " + projectName, true);
		if (!useCurrentProject) {
			projectName = ui.askWithDefault("Import onto which project?", "");
		}
		//We might have changed the environment, so recopy state into importer
		importer.copyScriptState(this);
		importer.recoverProjectFromProjectName(projectName);
	}

	protected void reviewExistingTaskOption() {
		//Do we want to import onto an existing task?
		if (ui.askYesNo("Import onto an existing task", false)) {
			String taskKey = ui.askOptional("Please enter the task ID to import onto");
			if (!StringUtils.isEmpty(taskKey)) {
				importer.setLastTaskCreated(taskKey);
				importer.setMode(MultiArchiveImporter.MODE.ALL_ARCHIVES_IN_ONE_TASK);
			}
		}
	}

	protected void reviewAuthor() {
		importer.promptForAuthor(ui);
	}
}
