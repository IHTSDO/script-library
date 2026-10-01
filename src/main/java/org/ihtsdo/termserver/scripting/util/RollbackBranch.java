package org.ihtsdo.termserver.scripting.util;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Branch;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class RollbackBranch extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(RollbackBranch.class);

	enum Mode { ROLLBACK_TO_PARENT, ROLLBACK_TO_TIME}

	private Mode mode = Mode.ROLLBACK_TO_TIME;

	LocalDateTime timeToRollBackTo = LocalDate.now().atTime(7, 0);


	public static void main(String[] args) throws TermServerScriptException {
		RollbackBranch importer = new RollbackBranch();
		try {
			ReportSheetManager.setTargetFolderId("13XiH3KVll3v0vipVxKwWjjf-wmjzgdDe"); //Technical Specialist Kung Foo
			importer.init(args);
			importer.postInit(new String[] {"Actions"}, new String[] {"Branch, HeadTimestamp, BaseTimestamp, Status, Action"});
			importer.rollbackBranch();
		} finally {
			importer.finish();
		}
	}

	/**
	 * Head, base and creation times as UTC, with the epoch milliseconds alongside, plus the branch state.
	 */
	public static String describe(Branch branch) {
		return branch.getName() + " " + describeState(branch, " ");
	}

	/**
	 * As describe(), without the branch name, with each item separated as required eg "\n" for a sheet cell.
	 */
	public static String describeState(Branch branch, String separator) {
		return "Head: " + formatTimestamp(branch.getHeadTimestamp()) + separator +
				"Base: " + formatTimestamp(branch.getBaseTimestamp()) + separator +
				"Created: " + formatTimestamp(branch.getCreationTimestamp()) + separator +
				"State: " + branch.getState();
	}

	public static String formatTimestamp(Long timestamp) {
		return timestamp == null ? "unknown" : Instant.ofEpochMilli(timestamp) + " (" + timestamp + ")";
	}

	private void rollbackBranch() throws TermServerScriptException {
		String branchPath = getProject().getBranchPath();
		String msg = "Rolling back " + branchPath;
		LOGGER.info(msg);
		report(PRIMARY_REPORT, msg);
		boolean userQuit = false;
		boolean rollBackToBaseline = false;
		while (!userQuit) {
			Branch branch = tsClient.getBranch(branchPath);
			boolean forceFurtherRollback = false;
			LocalDateTime head = LocalDateTime.ofInstant(Instant.ofEpochMilli(branch.getHeadTimestamp()), ZoneOffset.UTC);
			LocalDateTime base = LocalDateTime.ofInstant(Instant.ofEpochMilli(branch.getBaseTimestamp()), ZoneOffset.UTC);
			println("\n" + describe(branch));
			msg = "Rolled-Back";

			if (branch.getHeadTimestamp() <= branch.getBaseTimestamp() || branch.getState().equals("BEHIND")) {
				msg = "Final state - base timestamp reached / branch is behind parent";
				print("Force further rollback (F) or Quit (Q): ");
				String choice = STDIN.nextLine().trim().toUpperCase();
				if (choice.equals("F")) {
					forceFurtherRollback = true;
					rollBackToBaseline = false;
				} else {
					userQuit = true;
				}
			}

			if (!rollBackToBaseline || forceFurtherRollback) {
				print("Rollback (R), Rollback to Baseline (B) or Quit (Q): ");
				String choice = STDIN.nextLine().trim().toUpperCase();
				if (choice.equals("B")) {
					rollBackToBaseline = true;
				} else if (choice.equals("Q")) {
					userQuit = true;
					msg = "Final state";
				} else if (choice.equals("R")) {
					tsClient.adminRollbackCommit(branch);
				} else {
					throw new TermServerScriptException("Unknown Rollback choice: " + choice);
				}
			}

			if (!userQuit && rollBackToBaseline) {
				tsClient.adminRollbackCommit(branch);
			}
			report(PRIMARY_REPORT, "", head, base, branch.getState(), msg);
		}
	}


}
