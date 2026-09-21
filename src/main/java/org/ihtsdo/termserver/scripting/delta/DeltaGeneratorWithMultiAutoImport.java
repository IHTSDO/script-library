package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.FileUtils;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.util.MultiArchiveImporter;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Variant of DeltaGeneratorWithAutoImport for scripts that produce more than one archive
 * in a single run (see DeltaGenerator#createOutputArchive, which records each archive it
 * creates via addArchiveCreated when the running script is a DeltaGeneratorWithAutoImport).
 * Rather than renaming a single archive to the ticket name, all archives created during the
 * run are moved into a "<ticket>/import" directory under outputStoragePathBase, and that
 * directory is then processed the same way MultiArchiveImporter processes a directory when
 * run standalone - one task created per archive, each reported with the archive name and the
 * task it was loaded to.
 */
public class DeltaGeneratorWithMultiAutoImport extends DeltaGeneratorWithAutoImport {

	private static final String OUTPUT_STORAGE_PATH_BASE_ARG = "--storageBaseDir";

	private String outputStoragePathBase;

	@Override
	protected void init(String[] args) throws TermServerScriptException {
		outputStoragePathBase = extractArgValue(args, OUTPUT_STORAGE_PATH_BASE_ARG);
		super.init(args);
	}

	private static String extractArgValue(String[] args, String flag) {
		for (int i = 0; i < args.length - 1; i++) {
			if (args[i].equals(flag)) {
				return args[i + 1];
			}
		}
		return null;
	}

	@Override
	protected void importArchiveToTask(File ignored) throws TermServerScriptException {
		List<File> archivesCreated = getArchivesCreatedDuringThisRun();
		if (archivesCreated.isEmpty()) {
			println("No archives were created during this run, nothing to import.");
			return;
		}

		importer = new MultiArchiveImporter(this);
		if (!checkProceed()) {
			return;
		}

		promptForTaskPrefixIfNeeded();
		File importDir = moveArchivesToImportDirectory(archivesCreated);
		reviewEnvironmentAndProject();
		reviewExistingTaskOption();
		reviewAuthor();

		print("Ready to import " + archivesCreated.size() + " archive(s) into a task in " + projectName + "? Y/N [Y]: ");
		String response = STDIN.nextLine().trim();
		if (!response.equalsIgnoreCase("N")) {
			importer.setTaskPrefix(taskPrefix);
			importer.importArchives(importDir);
		}
	}

	private File moveArchivesToImportDirectory(List<File> archivesCreated) throws TermServerScriptException {
		if (StringUtils.isEmpty(outputStoragePathBase)) {
			print("Enter base path for archive storage (eg your Google Drive folder), or set via " + OUTPUT_STORAGE_PATH_BASE_ARG + ": ");
			outputStoragePathBase = STDIN.nextLine().trim();
		}

		File proposedImportDir = new File(new File(outputStoragePathBase, taskPrefix), "import");
		File importDir;
		try {
			importDir = FileUtils.createDirectoryOrIncrement(proposedImportDir);
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to create directory " + proposedImportDir, e);
		}

		for (File thisArchive : archivesCreated) {
			File dest = new File(importDir, thisArchive.getName());
			if (!thisArchive.renameTo(dest)) {
				throw new TermServerScriptException("Failed to move " + thisArchive + " to " + dest);
			}
		}
		println("Moved " + archivesCreated.size() + " archive(s) to " + importDir);
		return importDir;
	}
}
