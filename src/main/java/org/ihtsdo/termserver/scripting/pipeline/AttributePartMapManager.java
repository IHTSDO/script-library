package org.ihtsdo.termserver.scripting.pipeline;

import java.io.*;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.pipeline.domain.Part;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConcept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AttributePartMapManager extends AbstractMapManager {

	private static final Logger LOGGER = LoggerFactory.getLogger(AttributePartMapManager.class);

	protected Map<String, Part> parts;
	protected Map<String, List<Concept>> partToAttributeValueMap = new HashMap<>();
	protected Map<String, List<Concept>> hardCodedMappings = new HashMap<>();
	protected Map<Concept, Concept> knownReplacementMap = new HashMap<>();
	protected Map<Concept, Concept> hardCodedTypeReplacementMap = new HashMap<>();

	protected boolean allowStatusMapped = false;
	
	protected AttributePartMapManager(ContentPipelineManager cpm, Map<String, Part> parts) {
		super(cpm);
		this.parts = parts;
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
		validateHardCodedMappings();
		Set<String> partsSeen = new HashSet<>();
		List<String> mappingNotes = new ArrayList<>();

		loadMapFile(attributeMapFile, items -> processPartMapFileLine(items, partsSeen, mappingNotes));
		LOGGER.info("Populated map of {} parts to attributes", partToAttributeValueMap.size());
	}

	private void validateHardCodedMappings() throws TermServerScriptException {
		int invalidMapTargetCount = 0;
		for (Map.Entry<String, List<Concept>> entry : hardCodedMappings.entrySet()) {
			for (Concept mapTargetProto : entry.getValue()) {
				String conceptId = mapTargetProto.getConceptId();
				Concept mapTarget = gl.getConceptSafely(conceptId, false, false);
				if (mapTarget == null || mapTarget.getFSNDescription() == null) {
					LOGGER.warn("Invalid map target for part {}: {}", entry.getKey(), conceptId);
					invalidMapTargetCount++;
				}
			}
		}

		if (invalidMapTargetCount > 0) {
			throw new TermServerScriptException("Hard coded mappings contained " + invalidMapTargetCount + " invalid map targets");
		}
	}

	private void processPartMapFileLine(String[] items, Set<String> partsSeen, List<String> mappingNotes) throws TermServerScriptException {
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
			processAcceptedMapItem(items, partsSeen, mappingNotes, partNum);
		} else if (!items[ColIdx.idx(COL_STATUS)].equals("UNMAPPED")) {
			mappingNotes.add("Map indicates non-viable map status - " + items[ColIdx.idx(COL_STATUS)]);
		}

		if (!mappingNotes.isEmpty()) {
			mapNotes.put(partNum, String.join("\n", mappingNotes));
			mappingNotes.clear();
		}
	}

	private void processAcceptedMapItem(String[] items, Set<String> partsSeen, List<String> mappingNotes, String partNum) throws TermServerScriptException {
		try {
			partsSeen.add(partNum);
			Concept attributeValue = gl.getConcept(items[ColIdx.idx(COL_TARGET)], false, true);
			attributeValue = replaceValueIfRequired(mappingNotes, attributeValue);
			if (attributeValue != null && attributeValue.isActive()) {
				mappingNotes.add("Inactive concept");
			}
			partToAttributeValueMap.computeIfAbsent(partNum, k -> new ArrayList<>()).add(attributeValue);
		} catch (TermServerScriptException e) {
			String errMsg = e.getMessage();
			if (errMsg.startsWith("Unable")) {
				mappingNotes.add(e.getMessage());
			} else {
				throw e;
			}
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
}
