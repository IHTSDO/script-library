package org.ihtsdo.termserver.scripting.cis;

import com.google.common.collect.Lists;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.client.CisClient;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * In this case, the cookie passed in will not be the usual ims cookie,
 * but the cis token.  So removed the ihtsdo= prefix and use the token value directly.
 */
public class PublishSctids extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(PublishSctids.class);
	private static final String THIS_TICKET = "";
	private enum ACTION {PUBLISH, REGISTER}

	public static final String AVAILABLE = "Available";
	public static final String RESERVED = "Reserved";
	public static final String DEPRECATED = "Deprecated";
	public static final String ASSIGNED = "Assigned";
	public static final String PUBLISHED = "Published";

	private Map<String, Set<String>> newSctidsByNamespace = new HashMap<>();
	private Map<String, Set<String>> oldSctidsByNamespace = new HashMap<>();

	private List<String> targetNamespaces = null; // List.of("1000036")
	private Set<String> detectedNamespaces = new HashSet<>();
	private String targetET = null; //This is the ET we are interested in, which will be used to filter out old SCTIDs
	private int batchSize = 200;
	
	private boolean processRelationshipsOnly = false;
	private boolean includeLegacySCTIDS = true;
	private boolean publishInternationalSCTIDS = false;

	private CisClient cisClient;

	public static void main(String[] args) throws TermServerScriptException {
		PublishSctids report = new PublishSctids();
		try {
			report.localClientsRequired = false;
			report.summaryTabIdx = PRIMARY_REPORT;
			report.init(args);
			report.postInit();
			report.groupSCTIDsByNamespace("Snapshot", true);
			report.groupSCTIDsByNamespace("Full", false);
			report.filterOutOldSCTIDs();
			report.publishSCTIDS();
		} catch (Exception e) {
			LOGGER.error("Failed to publish sctids", e);
		} finally {
			report.finish();
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("13XiH3KVll3v0vipVxKwWjjf-wmjzgdDe");  // Technical Specialist
		String[] columnHeadings = new String[] {
				"Summary Item, Detail, ",
				"Request, JobId, Response,  , , ",
				"SCTID, JobId, Info, Status"};
		String[] tabNames = new String[] {
				"Summary",
				"Batch Request/Response",
				"SCTID Detail"};
		super.postInit(tabNames, columnHeadings);
	}

	private void filterOutOldSCTIDs() {
		if (includeLegacySCTIDS) {
			return;
		}

		for (String namespace : newSctidsByNamespace.keySet()) {
			 newSctidsByNamespace.get(namespace).removeAll(oldSctidsByNamespace.get(namespace));
		}
	}

	@Override
	public void init(String[] args) throws TermServerScriptException {
		super.init(args);
		//Set the secondary server URL using the -s or --server parameters
		cisClient = new CisClient(getSecondaryServerUrl(), authenticatedCookie);
	}

	private void publishSCTIDS() throws TermServerScriptException {
		if (newSctidsByNamespace.isEmpty()) {
			LOGGER.info("No SCTIDs to publish");
			report(PRIMARY_REPORT, "No SCTIDs detected to publish");
			return;
		}

		for (String namespace : newSctidsByNamespace.keySet()) {
			List<String> sctids = new ArrayList<>(newSctidsByNamespace.get(namespace));
			if ((!publishInternationalSCTIDS && namespace.equals("0"))
					|| sctids.isEmpty()) {
				LOGGER.info("Skipping {} sctids for namespace {}", sctids.size(), namespace);
				continue;
			}
			LOGGER.info("Processing {} sctids for namespace {}", sctids.size(), namespace);
			List<List<String>> batches = Lists.partition(sctids, batchSize);
			int batchCount = 0;
			int originalBatchSize = batches.size();  //This will change as we remove empty batches
			for (List<String> batch : batches) {
				String batchInfo = (++batchCount + "/" + originalBatchSize);

				//Filter out those records that are currently 'Reserved'
				//Take a copy of the list because the loop doesn't like becoming empty during processing!
				batch = removeAndReportReserved(namespace, new ArrayList<>(batch));
				//Have we lost all of them?
				if (batch.isEmpty()) {
					LOGGER.info("Skipping empty batch {} - no ids remain to be published after filtering.", batchInfo);
					continue;
				}
				LOGGER.debug("Processing batch {} of {} sctids", batchInfo, batch.size());
				transitionSCTIDS(batch, namespace, ACTION.PUBLISH);
				flushFilesWithWait(false);
			}
		}
	}

	private void transitionSCTIDS(List<String> batch, String namespace, ACTION action) throws TermServerScriptException {
		String actionStr = action.toString().toLowerCase();

		CisResponse response = null;
		String requestStr = null;
		switch (action) {
			case REGISTER:
				CisBulkRegisterRequest cisBulkRegisterRequest = new CisBulkRegisterRequest(THIS_TICKET + " Bulk " + actionStr + " of " + batch.size() + " sctids",
						Long.parseLong(namespace),
						batch,
						"Script-Library");
				requestStr = cisBulkRegisterRequest.toString();
				response = cisClient.registerSctids(cisBulkRegisterRequest);
				break;
			case PUBLISH:
				CisBulkRequest cisBulkRequest = new CisBulkRequest(THIS_TICKET + " Bulk " + actionStr + " of " + batch.size() + " sctids",
						Long.parseLong(namespace),
						batch,
						"Script-Library");
				requestStr = cisBulkRequest.toString();
				response = cisClient.publishSctids(cisBulkRequest);
				break;
			default:
				throw new TermServerScriptException("Unsupported action: " + action);
		}
		List<CisRecord> records = cisClient.getBulkJobBlocking(response.getId());
		report(SECONDARY_REPORT, requestStr, response.getId(), response);
		for (CisRecord record : records) {
			incrementSummaryInformation("SCTIDs " + actionStr + "ed namespace " + namespace);
			report(TERTIARY_REPORT, record.getSctid(), response.getId(), "", record.getStatus());
		}
		String actioned = StringUtils.capitalizeFirstLetter(actionStr) + "ed";
		LOGGER.info("{} {} SCTIDS for namespace {}", actioned, records.size(), namespace);
	}

	private List<String> removeAndReportReserved(String namespace, List<String> batch) throws TermServerScriptException {
		//We need to recover the current state so we can 'Assign' SCTIDs that are currently only at Status 'Reserved'
		List<CisRecord> currentStatus = cisClient.getSCTIDs(batch);
		List<String> availableSCTIDs = new ArrayList<>();
		for (CisRecord cisRecord : currentStatus) {
			if (cisRecord.getStatus().equals(RESERVED)) {
				incrementSummaryInformation("SCTIDs stuck at reserved");
				report(TERTIARY_REPORT, cisRecord.getSctid(), "", "SCTID is currently reserved. Cannot assign without calculating systemId", cisRecord);
				batch.remove(cisRecord.getSctid().toString());
			} else if (cisRecord.getStatus().equals(PUBLISHED)) {
				incrementSummaryInformation("SCTIDs already published");
				report(TERTIARY_REPORT, cisRecord.getSctid(), "", "SCTID is already published.", cisRecord);
				batch.remove(cisRecord.getSctid().toString());
			} else if (cisRecord.getStatus().equals(AVAILABLE)) {
				//We'll move AVAILABLE SCTIDs to ASSIGNED here, and then allow them to follow on to be published by the calling method
				incrementSummaryInformation("SCTIDs state " + cisRecord.getStatus());
				report(TERTIARY_REPORT, cisRecord.getSctid(), "", "SCTID status: " + cisRecord.getStatus() + " moving to be ASSIGNED (prior to publishing)", cisRecord);
				availableSCTIDs.add(cisRecord.getSctid().toString());
			}
		}

		if (!availableSCTIDs.isEmpty()) {
			transitionSCTIDS(availableSCTIDs, namespace, ACTION.REGISTER);
		}

		return batch;
	}

	private void groupSCTIDsByNamespace(String fileType, boolean findNewSCTIDs) {
		Map<String, Set<String>> sctidsByNamespace = findNewSCTIDs ? newSctidsByNamespace : oldSctidsByNamespace;

		try (InputStream is = new FileInputStream("releases/" + projectName);
			 ZipInputStream zis = new ZipInputStream(is)) {

			LOGGER.info("Processing : {} for {} files", projectName, fileType);
			processZipEntries(zis, fileType, findNewSCTIDs, sctidsByNamespace);

		} catch (IOException e) {
			LOGGER.error("Failed to process {}", projectName, e);
		}
	}

	private void processZipEntries(ZipInputStream zis,
									String fileType,
									boolean findNewSCTIDs,
									Map<String, Set<String>> sctidsByNamespace) throws IOException {

		ZipEntry entry;
		while ((entry = zis.getNextEntry()) != null) {
			if (shouldProcessEntry(entry, fileType)) {
				LOGGER.info("Processing {}", entry.getName());
				processZipEntryContent(zis, findNewSCTIDs, sctidsByNamespace);
			}
		}
	}

	private boolean shouldProcessEntry(ZipEntry entry, String fileType) {
		String name = entry.getName();

		return name.endsWith(".txt")
				&& (!processRelationshipsOnly || name.contains("Relationship"))
				&& name.contains(fileType)
				&& !name.contains("Identifier")
				&& !name.contains("Readme");
	}

	private void processZipEntryContent(ZipInputStream zis,
										boolean findNewSCTIDs,
										Map<String, Set<String>> sctidsByNamespace) throws IOException {

		BufferedReader br = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
		String line;

		while ((line = br.readLine()) != null) {
			processLine(line, findNewSCTIDs, sctidsByNamespace);
		}
	}

	private void processLine(String line,
							 boolean findNewSCTIDs,
							 Map<String, Set<String>> sctidsByNamespace) {

		String[] parts = line.split("\t");
		String sctid = parts[0];
		String effectiveTime = parts[1];

		if (shouldSkipLine(sctid, effectiveTime, findNewSCTIDs)) {
			return;
		}
		String namespace = resolveNamespace(sctid);
		if (targetNamespaces != null && !targetNamespaces.contains(namespace)) {
			return;
		}
		
		addSctidToNamespaceMap(namespace, sctid, sctidsByNamespace);
	}

	private boolean shouldSkipLine(String sctid, String effectiveTime, boolean findNewSCTIDs) {

		if (!includeLegacySCTIDS && findNewSCTIDs != effectiveTime.equals(this.targetET)) {
			return true;
		}

		return sctid.equals("id") || sctid.contains("-");
	}

	private String resolveNamespace(String sctid) {
		for (String knownNamespace : detectedNamespaces) {
			if (sctid.contains(knownNamespace)) {
				return knownNamespace;
			}
		}
		return SnomedUtilsBase.getNamespace(sctid);
	}

	private void addSctidToNamespaceMap(String namespace,
										String sctid,
										Map<String, Set<String>> sctidsByNamespace) {

		sctidsByNamespace
				.computeIfAbsent(namespace, k -> new HashSet<>())
				.add(sctid);
	}


}
