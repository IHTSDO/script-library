package org.ihtsdo.termserver.scripting.snapshot;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

public class SnapshotStats extends TermServerReport {

	public static void main(String[] args) throws TermServerScriptException {
		new SnapshotStats().standardExecution(args, ExecutionOptions.DEFAULT);
	}

	@Override
	public void runJob() throws TermServerScriptException {
		Map<String, Integer> namespaceCounts = countExtensionNamespaces(gl.getAllConcepts());
		for (Map.Entry<String, Integer> entry : namespaceCounts.entrySet()) {
			report(PRIMARY_REPORT, entry.getKey(),entry.getValue());
		}
	}

	public static Map<String, Integer> countExtensionNamespaces(Collection<Concept> concepts) {
		Map<String, Integer> namespaceCounts = new TreeMap<>();

		for (Concept c : concepts) {
			String sctIdStr = c.getId();

			// Minimum length check: namespace(7) + partition(2) + check digit(1)
			if (sctIdStr.length() < 10) {
				continue;
			}

			// Partition ID is the two digits before the check digit
			int len = sctIdStr.length();
			String partitionId = sctIdStr.substring(len - 3, len - 1);

			// Only extension concepts (partitionId == "10")
			if (!"10".equals(partitionId)) {
				continue;
			}

			// Namespace is the 7 digits immediately before the partitionId
			int namespaceStart = len - 3 - 7;
			String namespace = sctIdStr.substring(namespaceStart, namespaceStart + 7);
			namespaceCounts.merge(namespace, 1, Integer::sum);
		}

		return namespaceCounts;
	}

}
