package org.ihtsdo.termserver.scripting.task;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.authoringservices.AuthoringServicesClient;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.JobClass;
import org.ihtsdo.termserver.scripting.domain.ExecutionOptions;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.Job;

import java.util.List;

public class DeleteAllTasksOnProject extends TermServerReport implements JobClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(DeleteAllTasksOnProject.class);

	private static List<String> except = List.of("PDE-465");

	public static void main(String[] args) throws TermServerScriptException {
		ExecutionOptions options = new ExecutionOptions().withNoSnapshotImport();
		new DeleteAllTasksOnProject().standardExecution(args, options);
	}

	@Override
	public Job getJob() {
		return null;
	}

	@Override
	public void runJob() throws TermServerScriptException {
		//Recover all active tasks from project and mark as deleted
		AuthoringServicesClient client = getAuthoringServicesClient();
		for (Task t : client.listTasksOnProject(getProject().getKey())) {
			if (!except.contains(t.getKey())) {
				LOGGER.info("Deleting task {}", t.getKey());
				t.setStatus("DELETED");
				client.updateTask(getProject().getKey(), t);
				report(PRIMARY_REPORT, t);
			}
		}
	}
}
