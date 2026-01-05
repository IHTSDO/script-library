package org.ihtsdo.termserver.scripting.pipeline;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.apache.commons.io.input.BOMInputStream;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.pipeline.domain.Part;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConcept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AttributePartMapManager implements ContentPipeLineConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(AttributePartMapManager.class);
	private static final String MAP_IMPORT = "Map Import";

	// Fixed header names
	public static final String COL_PART_NUM = "Source code";
	public static final String COL_STATUS = "Status";
	public static final String COL_NO_MAP = "No map flag";
	public static final String COL_TARGET = "Target code";

	protected ContentPipelineManager cpm;
	protected GraphLoader gl;
	protected Map<String, Part> parts;
	protected Map<String, List<Concept>> partToAttributeValueMap = new HashMap<>();
	protected Map<String, List<Concept>> hardCodedMappings = new HashMap<>();
	protected Map<Concept, Concept> knownReplacementMap = new HashMap<>();
	protected Map<Concept, Concept> hardCodedTypeReplacementMap = new HashMap<>();
	protected final Map<String, String> partMapNotes;

	protected boolean allowStatusMapped = false;
	
	protected AttributePartMapManager(ContentPipelineManager cpm, Map<String, Part> parts, Map<String, String> partMapNotes) {
		this.cpm = cpm;
		this.gl = cpm.getGraphLoader();
		this.parts = parts;
		this.partMapNotes = partMapNotes;
	}

	protected abstract void populateConceptReplacements() throws TermServerScriptException;

	public List<RelationshipTemplate> getPartMappedAttributeForType(TemplatedConcept tc, String partNum, Concept attributeType) throws TermServerScriptException {
		if (SnomedUtils.isEmpty(partNum)) {
			//Can't look up an unspecified part.
			//In the case of, eg NPU Unit not being specified, this is fine.
		} else if (hardCodedMappings.containsKey(partNum)) {
			return extractPartMappingFromMapAsRelationshipTemplate(hardCodedMappings, partNum, attributeType);
		} else if (containsMappingForPartNum(partNum)) {
			return extractPartMappingFromMapAsRelationshipTemplate(partToAttributeValueMap, partNum, attributeType);
		} else if (!cpm.getMappingsAllowedAbsent().contains(partNum)) {
			//Some special rules exist for certain parts, so we don't need to report if we have one of those.
			String partStr = parts.get(partNum) == null ? "Part Not Known - " + partNum : parts.get(partNum).toString();
			tc.getConcept().addIssue("No attribute mapping available for " + partStr);
			cpm.addMissingMapping(partNum, tc.getExternalIdentifier());
		}
		return new ArrayList<>();
	}

	private List<RelationshipTemplate> extractPartMappingFromMapAsRelationshipTemplate(Map<String, List<Concept>> map, String partNum, Concept attributeType) {
		List<RelationshipTemplate> mappings = new ArrayList<>();
		for (Concept attributeValue : map.get(partNum)) {
			mappings.add(new RelationshipTemplate(attributeType, attributeValue));
		}
		return mappings;
	}

	public void populatePartAttributeMap(File attributeMapFile) throws TermServerScriptException {
		// Output format from Snap2SNOMED is expected to be:
		// Source code[0]   Source display  Status  PartTypeName    Target code[4]  Target display  Relationship type code  Relationship type display   No map flag[8] Status[9]
		populateConceptReplacements();
		populateHardCodedMappings();
		int lineNum = 0;
		int problemsEncountered = 0;
		Set<String> partsSeen = new HashSet<>();
		boolean fileLoadedSuccessfully = true;
		List<String> mappingNotes = new ArrayList<>();

		try {
			LOGGER.info("Loading Part Attribute Map File: {}", attributeMapFile);
			try (
					BOMInputStream bomIn = BOMInputStream.builder()
							.setInputStream(new FileInputStream(attributeMapFile))
							// .setByteOrderMarks(...)   // optionally specify which BOMs to detect (defaults to UTF-8)
							.setInclude(false)           // whether to include the BOM in the stream or exclude it
							.get();
					InputStreamReader isr = new InputStreamReader(bomIn, StandardCharsets.UTF_8);
					BufferedReader br = new BufferedReader(isr)
			) {
				String line;
				while ((line = br.readLine()) != null) {
					lineNum++;
					if (lineNum == 1) {
						// Header line - discover indexes
						ColIdx.initialize(line);
					} else if (!line.isEmpty()) {
						try {
							processPartMapFileLine(line, partsSeen, mappingNotes);
						} catch (TermServerScriptException e) {
							fileLoadedSuccessfully = false;
							problemsEncountered++;
							LOGGER.warn("Problem processing line {}: {}", lineNum, e.getMessage());
						}
					}
				}
			}
			LOGGER.info("Populated map of {} parts to attributes", partToAttributeValueMap.size());
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to read " + attributeMapFile + " at line " + lineNum, e);
		}

		if (!fileLoadedSuccessfully) {
			//throw new TermServerScriptException("Failed to read " + attributeMapFile + ". See log for individual line issues, total issue count " + problemsEncountered);
		}
	}

	private void processPartMapFileLine(String line, Set<String> partsSeen, List<String> mappingNotes) throws TermServerScriptException {
		String[] items = line.split("\t");
		String partNum = items[ColIdx.idx(COL_PART_NUM)];

		//Quick check that the part we're talking about here is actually known to the input files
		Part part = cpm.getPart(partNum);
		if (part == null) {
			throw new TermServerScriptException("Part " + partNum + " listed in map but not known to parts file. Target was " + items[ColIdx.idx(COL_STATUS)] + " as '" + items[ColIdx.idx(COL_TARGET)]  + "'");
		}

		//Note that we could have multiple lines for the same partNum in a 1:many mapping
		if (items[ColIdx.idx(COL_NO_MAP)].equals("true")) {
			//And we can have items that report being mapped, but with 'no map' - warn about those.
			mappingNotes.add("Map indicates part mapped to 'No Map'");
		} else if (items[ColIdx.idx(COL_STATUS)].equals("ACCEPTED") ||
				(allowStatusMapped && items[ColIdx.idx(COL_STATUS)].equals("MAPPED"))) {
			partsSeen.add(partNum);
			Concept attributeValue = gl.getConcept(items[ColIdx.idx(COL_TARGET)], false, true);
			attributeValue = replaceValueIfRequired(mappingNotes, attributeValue);
			if (attributeValue != null && attributeValue.isActive()) {
				mappingNotes.add("Inactive concept");
			}
			partToAttributeValueMap.computeIfAbsent(partNum, k -> new ArrayList<>()).add(attributeValue);
		} else if (items[ColIdx.idx(COL_STATUS)].equals("UNMAPPED")) {
			//Skip this one without mentioning it
		} else {
			mappingNotes.add("Map indicates non-viable map status - " + items[ColIdx.idx(COL_STATUS)]);
		}

		if (!mappingNotes.isEmpty()) {
			partMapNotes.put(partNum, String.join("\n", mappingNotes));
			mappingNotes.clear();
		}
	}

	public Concept replaceValueIfRequired(List<String> mappingNotes, Concept attributeValue) {

		if (!attributeValue.isActiveSafely()) {
			String hardCodedIndicator = " hardcoded";
			Concept replacementValue = knownReplacementMap.get(attributeValue);
			if (replacementValue == null) {
				hardCodedIndicator = "";
				replacementValue = cpm.getReplacementSafely(mappingNotes, attributeValue, false);
			}

			String replacementMsg = replacementValue == null ? "  no replacement available." : hardCodedIndicator + " replaced with " + replacementValue;
			String successStr = replacementValue == null ? "Unsuccessful" : "Successful";
			cpm.incrementSummaryCount(MAP_IMPORT, successStr + " value replacement");
			String prefix = replacementValue == null ? "* " : "";
			mappingNotes.add(prefix + "Mapped to" + hardCodedIndicator + " inactive value: " + attributeValue + replacementMsg);

			if (replacementValue != null) {
				attributeValue = replacementValue;
			}
		}
		return attributeValue;
	}

	public Concept replaceTypeIfRequired(List<String> mappingNotes, Concept attributeType) {
		if (hardCodedTypeReplacementMap.containsKey(attributeType)) {
			attributeType = hardCodedTypeReplacementMap.get(attributeType);
		}

		if (!attributeType.isActiveSafely()) {
			String hardCodedIndicator = " hardcoded";
			Concept replacementType = knownReplacementMap.get(attributeType);
			if (replacementType == null) {
				hardCodedIndicator = "";
				replacementType = cpm.getReplacementSafely(mappingNotes, attributeType, false);
			}
			String replacementMsg = replacementType == null ? " no replacement available." : hardCodedIndicator + " replaced with " + replacementType;
			String successStr = replacementType == null ? "Unsuccessful" : "Successful";
			cpm.incrementSummaryCount(MAP_IMPORT, successStr + " type replacement");
			mappingNotes.add("Mapped to" + hardCodedIndicator + " inactive type: " + attributeType + replacementMsg);

			if (replacementType != null) {
				attributeType = replacementType;
			}
		}
		return attributeType;
	}

	public boolean containsMappingForPartNum(String loincPartNum) {
		return partToAttributeValueMap.containsKey(loincPartNum);
	}

	protected abstract void populateHardCodedMappings() throws TermServerScriptException;

	public void allowStatusMapped(boolean allowStatusMapped) {
		this.allowStatusMapped = allowStatusMapped;
	}

	public static final class ColIdx {
		// Runtime-discovered indexes
		private static final Map<String, Integer> indexMap = new HashMap<>();

		/** Discover column positions from header line */
		public static void initialize(String headerLine) {
			String[] headers = headerLine.split("\t", -1);
			for (int i = 0; i < headers.length; i++) {
				indexMap.put(headers[i].trim(), i);
			}
		}

		/** Retrieve index for a given column name */
		public static int idx(String columnName) {
			Integer idx = indexMap.get(columnName);
			if (idx == null) {
				throw new IllegalStateException("Column not found in header: " + columnName);
			}
			return idx;
		}
	}
}
