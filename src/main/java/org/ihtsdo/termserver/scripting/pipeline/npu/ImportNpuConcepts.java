package org.ihtsdo.termserver.scripting.pipeline.npu;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipelineManager;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.domain.Part;
import org.ihtsdo.termserver.scripting.pipeline.npu.domain.NpuConcept;
import org.ihtsdo.termserver.scripting.pipeline.npu.domain.NpuDetail;
import org.ihtsdo.termserver.scripting.pipeline.npu.domain.NpuPart;
import org.ihtsdo.termserver.scripting.pipeline.npu.template.NpuTemplatedConcept;
import org.ihtsdo.termserver.scripting.pipeline.npu.template.NpuTemplatedConceptWithComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

public class ImportNpuConcepts extends ContentPipelineManager implements NpuScriptConstants {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ImportNpuConcepts.class);

	private static final boolean PRODUCE_LIST_OF_PARTS = false;

	private static final int FILE_IDX_NPU_PARTS_MAP_BASE_FILE = 1;

	Map<String, NpuDetail> npuDetailsMap = new HashMap<>();

	protected String[] tabNames = new String[] {
			TAB_SUMMARY,
			TAB_MAP_ISSUES,
			TAB_MODELING_ISSUES,
			TAB_PROPOSED_MODEL_COMPARISON,
			TAB_MAP_ME,
			TAB_IMPORT_STATUS,
			TAB_STATS};

	public static void main(String[] args) throws TermServerScriptException {
		new ImportNpuConcepts().ingestExternalContent(args);
	}

	protected String[] getTabNames() {
		return tabNames;
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"npu_code, shortDefinition, system, component, kindOfProperty, proc, unit, specialty, contextDependent, group, scaleType, active, , ",
				"Source, Issue, , , ",
				"npu_code, Item of Interest, External Concept Long Name, ColumnName, Part Status, SCTID, FSN, Priority Index, Usage Count, Top Priority Usage, Mapping Notes,",
				"NpuNum, Item of Interest, SCTID, This Iteration, Template, Differences, Proposed Descriptions, Previous Descriptions, Proposed Model, Previous Model, ShortName, System, Component, Property, Proc, Unit, , , , , , , , , , , , , , , , , ",
				"NPU Element Code, Element Name, Category, High Usage, Highest Usage, , Concepts Affected, , , ",
				"PartNum, PartName, PartType, Needed for High Usage Mapping, Needed for Highest Usage Mapping, PriorityIndex, Usage Count,Top Priority Usage, Higest Rank, HighestUsageCount",
				"Category, NpuNum, Detail, , , "
		};

		super.postInit(GFOLDER_NPU, tabNames, columnHeadings, false);

		getReportManager().disableTab(getTab(TAB_IMPORT_STATUS));
		//getReportManager().disableTab(getTab(TAB_ITEMS_OF_INTEREST));

		scheme = gl.getConcept(SCTID_NPU_SCHEMA);
		externalContentModuleId = SCTID_NPU_EXTENSION_MODULE;
		namespace = "1003000";
		this.setPrimaryLangRefset(GB_ENG_LANG_REFSET);
		this.setIncludeShortNameAsPreferredTerm(true);
		this.setIncludeLongNameDescription(true);

		PART_OF_INTEREST_MAP.putAll(Map.of(
				"QU101474", "NPU-26"
		));
	}

	@Override
	protected String getContentType() {
		return "Observable";
	}

	@Override
	protected void loadSupportingInformation() throws TermServerScriptException {
		importNpuConcepts();
		importNpuParts();
		importNpuDetail();
		loadPanels();

		if (PRODUCE_LIST_OF_PARTS) {
			produceListOfParts();
		}
	}

	private void importNpuDetail() throws TermServerScriptException {
		//Read in tab delimited FILE_IDX_NPU_DETAIL and create NpuDetail objects
		try {
			List<String> lines = FileUtils.readLines(getInputFile(FILE_IDX_NPU_DETAIL), StandardCharsets.UTF_8);
			boolean isHeader = true;
			for (String line : lines) {
				if (isHeader) {
					isHeader = false;
					continue;
				}
				String[] columns = line.split("\t", -1);
				NpuDetail npuDetail = NpuDetail.parse(columns);
				npuDetailsMap.put(npuDetail.getNpuCode(), npuDetail);
				NpuConcept npuConcept = (NpuConcept)getExternalConcept(npuDetail.getNpuCode());
				if (npuConcept == null) {
					LOGGER.debug("NPU Concept not found for NPU code: {}", npuDetail.getNpuCode());
				} else {
					//In the case of NPU, the parts are not given separately, but can be pulled out of the details
					for (Part part : npuDetail.getParts(npuConcept)) {
						partMap.put(part.getPartNumber(), part);
					}
				}
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to read NPU detail file", e);
		}
	}

	private void produceListOfParts() {
		Set<Part> parts = new TreeSet<>();
		for (Map.Entry<String, ExternalConcept> entry : externalConceptMap.entrySet()) {
			NpuConcept npuConcept = (NpuConcept) entry.getValue();
			parts.addAll(npuConcept.getParts());
		}

		//Write out the parts to a local file, tab delimited
		String[] columnHeadings = new String[] {
				"PartNum\tPart Display\tPart Category"
		};

		LOGGER.info("Writing parts list to parts_list.txt");
		try (FileWriter writer = new FileWriter("parts_list.txt")) {
			// Write column headings
			writer.write(String.join("\t", columnHeadings) + "\n");

			// Write each part
			for (Part part : parts) {
				writer.write(String.join("\t",
						part.getPartNumber(),
						part.getPartNumber(),
						part.getPartCategory()
				) + "\n");
			}
		} catch (IOException e) {
			LOGGER.error("Failed to write parts list", e);
		}
		System.exit(0);
	}

	private void importNpuConcepts() throws TermServerScriptException {
		ObjectMapper mapper = new XmlMapper();
		TypeReference<List<NpuConcept>> listType = new TypeReference<>(){};
		try {
			File conceptFile = getInputFile(FILE_IDX_NPU_FULL);
			LOGGER.info("Importing NPU Concepts from file: {}", conceptFile);
			FileInputStream is = FileUtils.openInputStream(conceptFile);
			List<NpuConcept> npuConcepts = mapper.readValue(is, listType);
			externalConceptMap = npuConcepts.stream()
					.filter(NpuConcept::isCurrentVersion)
				.collect(Collectors
						.toMap(NpuConcept::getExternalIdentifier,
								c -> c));
				} catch (IOException e) {
			throw new TermServerScriptException(e);
		}
		LOGGER.info("Loaded {} NPU Concepts", externalConceptMap.size());
	}

	private void importNpuParts() throws TermServerScriptException {
		ObjectMapper mapper = new XmlMapper();
		TypeReference<List<NpuPart>> listType = new TypeReference<>(){};
		try {
			File partFile = getInputFile(FILE_IDX_NPU_PARTS);
			LOGGER.info("Importing NPU Parts from file: {}", partFile);
			FileInputStream is = FileUtils.openInputStream(partFile);
			List<NpuPart> npuParts = mapper.readValue(is, listType);
			partMap = npuParts.stream()
					.filter(NpuPart::isCurrentVersion)
					.collect(Collectors.toMap(NpuPart::getPartNumber, p -> p ));
		} catch (IOException e) {
			throw new TermServerScriptException(e);
		}
		LOGGER.info("Loaded {} NPU Parts", partMap.size());
	}

	private void loadPanels() {
		//Populate panelNpuNums from file
	}

	@Override
	protected void importPartMap() throws TermServerScriptException {
		attributePartMapManager = new NpuAttributePartMapManager(this, partMap, partMapNotes);
		attributePartMapManager.allowStatusMapped(true);
		attributePartMapManager.populatePartAttributeMap(getInputFile(FILE_IDX_NPU_PARTS_MAP_BASE_FILE));
		NpuTemplatedConcept.initialise(this, npuDetailsMap);
	}

	@Override
	protected List<String> getExternalConceptsToModel() throws TermServerScriptException {
		try {
			//If we didn't specify a file, try for all of them
			File filterList = getInputFile(FILE_IDX_NPU_TECH_PREVIEW_CONCEPTS);
			if (filterList == null) {
				return externalConceptMap.keySet().stream().sorted().toList();
			}
			return FileUtils.readLines(getInputFile(FILE_IDX_NPU_TECH_PREVIEW_CONCEPTS), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to read NPU codes from file", e);
		}
	}

	@Override
	public NpuTemplatedConcept getAppropriateTemplate(ExternalConcept externalConcept) throws TermServerScriptException {
		return NpuTemplatedConceptWithComponent.create(externalConcept);
	}

	public NpuTemplatedConcept populateTemplate(ExternalConcept externalConcept) throws TermServerScriptException {
		NpuTemplatedConcept templatedConcept = getAppropriateTemplate(externalConcept);
		if (templatedConcept != null) {
			templatedConcept.populateTemplate();
		} else if (externalConcept.isHighUsage()) {
			//This is a highest usage term which is out of scope
			incrementSummaryCount(ContentPipelineManager.HIGH_USAGE_COUNTS, "High Usage Out of Scope");
			if (externalConcept.isHighestUsage()) {
				incrementSummaryCount(ContentPipelineManager.HIGHEST_USAGE_COUNTS, "Highest Usage Out of Scope");
			}
		}
		return templatedConcept;
	}

	@Override
	protected Set<String> getObjectionableWords() {
		return new HashSet<>();  //No objections here
	}

	@Override
	public List<String> getMappingsAllowedAbsent() {
		return new ArrayList<>();  //Not yet expecting to allow missin mappings
	}

	@Override
	protected String[] getHighUsageIndicators(Set<ExternalConcept> externalConcepts) {
		return new String[]{"N/A", "N/A", "N/A", "N/A", "N/A"};
	}

	public Map<String, NpuDetail> getDetailsMap() {
		return npuDetailsMap;
	}
}
