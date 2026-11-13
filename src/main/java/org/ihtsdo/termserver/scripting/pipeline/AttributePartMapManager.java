package org.ihtsdo.termserver.scripting.pipeline;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.ihtsdo.otf.RF2Constants.Severity;
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

	//For the Map Stats tab
	private final Map<String, Set<String>> mapFilePartsByStatus = new TreeMap<>();
	private final Set<String> noMapParts = new HashSet<>();
	private final Set<String> usedMapFileMappings = ConcurrentHashMap.newKeySet();
	private final Set<String> usedHardCodedMappings = ConcurrentHashMap.newKeySet();

	private static final String STATUS_ACCEPTED = "ACCEPTED";
	private static final String STATUS_MAPPED = "MAPPED";
	private static final String STATUS_UNMAPPED = "UNMAPPED";
	private static final String STAT_STATUS_PREFIX = "Map file parts with status ";
	private static final String STAT_PARTS = "Parts in parts file";
	private static final String STAT_PARTS_ABSENT = "Parts file parts absent from map file";
	private static final String STAT_NO_MAP = "Map file parts flagged No Map";
	private static final String STAT_MAP_FILE_MAPPINGS = "Map file mappings";
	private static final String STAT_MAP_FILE_USED = "Map file mappings used in concepts";
	private static final String STAT_MAP_FILE_UNUSED = "Map file mappings never used";
	private static final String STAT_MAP_FILE_ONE_TO_MANY = "Map file one-to-many mappings";
	private static final String STAT_VALUE_REPLACED = "Map targets inactive, replacement used";
	private static final String STAT_VALUE_NO_REPLACEMENT = "Map targets inactive, no replacement (left unmapped)";
	private static final String STAT_TARGET_NOT_FOUND = "Map targets not found";
	private static final String STAT_TYPE_REPLACED = "Map types inactive, replacement used";
	private static final String STAT_TYPE_NO_REPLACEMENT = "Map types inactive, no replacement";
	private static final String STAT_HARD_CODED = "Hard coded mappings";
	private static final String STAT_HARD_CODED_USED = "Hard coded mappings used in concepts";
	private static final String STAT_HARD_CODED_UNUSED = "Hard coded mappings never used";
	private static final String STAT_HARD_CODED_ONE_TO_MANY = "Hard coded one-to-many mappings";
	private static final String STAT_HARD_CODED_IN_MAP_FILE = "Hard coded mappings also in map file";
	//The order the Map Stats tab lists them in.  Any other map statuses follow.
	private static final List<String> MAP_STATS_IN_ORDER = List.of(
			STAT_PARTS, STAT_PARTS_ABSENT,
			STAT_STATUS_PREFIX + STATUS_ACCEPTED, STAT_STATUS_PREFIX + STATUS_MAPPED, STAT_STATUS_PREFIX + STATUS_UNMAPPED,
			STAT_NO_MAP, STAT_MAP_FILE_MAPPINGS, STAT_MAP_FILE_USED, STAT_MAP_FILE_UNUSED, STAT_MAP_FILE_ONE_TO_MANY,
			STAT_VALUE_REPLACED, STAT_VALUE_NO_REPLACEMENT, STAT_TARGET_NOT_FOUND,
			STAT_HARD_CODED, STAT_HARD_CODED_USED, STAT_HARD_CODED_UNUSED, STAT_HARD_CODED_ONE_TO_MANY, STAT_HARD_CODED_IN_MAP_FILE);
	
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
			usedHardCodedMappings.add(partNum);
			return extractPartMappingFromMapAsRelationshipTemplate(hardCodedMappings, partNum, attributeType);
		} else if (containsMappingForPartNum(partNum)) {
			usedMapFileMappings.add(partNum);
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
		MAP_STATS_IN_ORDER.forEach(cpm::initialiseMapStat);
		populateConceptReplacements();
		populateHardCodedMappings();
		validateHardCodedMappings();
		Set<String> partsSeen = new HashSet<>();

		loadMapFile(attributeMapFile, items -> processPartMapFileLine(items, partsSeen));
		LOGGER.info("Populated map of {} parts to attributes", partToAttributeValueMap.size());
		reportHardCodedMappingsAlsoInMapFile();
	}

	/**
	 * The hard coded mapping takes precedence, so the map file's mapping for the same part is never used.
	 * Where both map to the same concepts that has no effect, but where they differ the map file is being ignored.
	 */
	private void reportHardCodedMappingsAlsoInMapFile() throws TermServerScriptException {
		for (String partNum : getHardCodedMappingsAlsoInMapFile()) {
			List<Concept> hardCoded = hardCodedMappings.get(partNum);
			List<Concept> mapFile = partToAttributeValueMap.get(partNum);
			boolean sameConcepts = getConceptIds(hardCoded).equals(getConceptIds(mapFile));
			Severity severity = sameConcepts ? Severity.LOW : Severity.CRITICAL;
			String issue = sameConcepts ? "Hard coded mapping overrides map file mapping" : "Hard coded mapping differs from map file mapping";
			cpm.reportMapIssue(partNum, "", severity, issue, "Hard coded: " + hardCoded + "\nMap file: " + mapFile);
		}
	}

	private Set<String> getConceptIds(List<Concept> concepts) {
		Set<String> conceptIds = new TreeSet<>();
		for (Concept concept : concepts) {
			conceptIds.add(concept.getConceptId());
		}
		return conceptIds;
	}

	private Set<String> getHardCodedMappingsAlsoInMapFile() {
		Set<String> overlap = new TreeSet<>(hardCodedMappings.keySet());
		overlap.retainAll(partToAttributeValueMap.keySet());
		return overlap;
	}

	/**
	 * Passes the totals, which are only known once modelling is complete, to the content pipeline's map stats
	 */
	public void recordMapStats() {
		Set<String> mapFileParts = new HashSet<>();
		mapFilePartsByStatus.values().forEach(mapFileParts::addAll);
		Set<String> partsAbsentFromMapFile = new HashSet<>(parts.keySet());
		partsAbsentFromMapFile.removeAll(mapFileParts);

		cpm.setMapStat(STAT_PARTS, parts.size());
		cpm.setMapStat(STAT_PARTS_ABSENT, partsAbsentFromMapFile.size());
		for (Map.Entry<String, Set<String>> entry : mapFilePartsByStatus.entrySet()) {
			cpm.setMapStat(STAT_STATUS_PREFIX + entry.getKey(), entry.getValue().size());
		}
		cpm.setMapStat(STAT_NO_MAP, noMapParts.size());
		cpm.setMapStat(STAT_MAP_FILE_MAPPINGS, partToAttributeValueMap.size());
		cpm.setMapStat(STAT_MAP_FILE_USED, usedMapFileMappings.size());
		cpm.setMapStat(STAT_MAP_FILE_UNUSED, partToAttributeValueMap.size() - usedMapFileMappings.size());
		cpm.setMapStat(STAT_MAP_FILE_ONE_TO_MANY, countOneToMany(partToAttributeValueMap));
		cpm.setMapStat(STAT_HARD_CODED, hardCodedMappings.size());
		cpm.setMapStat(STAT_HARD_CODED_USED, usedHardCodedMappings.size());
		cpm.setMapStat(STAT_HARD_CODED_UNUSED, hardCodedMappings.size() - usedHardCodedMappings.size());
		cpm.setMapStat(STAT_HARD_CODED_ONE_TO_MANY, countOneToMany(hardCodedMappings));
		cpm.setMapStat(STAT_HARD_CODED_IN_MAP_FILE, getHardCodedMappingsAlsoInMapFile().size());
	}

	private long countOneToMany(Map<String, List<Concept>> mappings) {
		return mappings.values().stream().filter(targets -> targets.size() > 1).count();
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

	private void processPartMapFileLine(String[] items, Set<String> partsSeen) throws TermServerScriptException {
		String partNum = items[ColIdx.idx(COL_PART_NUM)];
		String status = items[ColIdx.idx(COL_STATUS)];

		//Quick check that the part we're talking about here is actually known to the input files
		Part part = cpm.getPart(partNum);
		if (part == null) {
			throw new TermServerScriptException("Part " + partNum + " listed in map but not known to parts file. Target was " + status + " as '" + items[ColIdx.idx(COL_TARGET)]  + "'");
		}

		mapFilePartsByStatus.computeIfAbsent(status, k -> new TreeSet<>()).add(partNum);

		//Note that we could have multiple lines for the same partNum in a 1:many mapping
		if (items[ColIdx.idx(COL_NO_MAP)].equals("true")) {
			//And we can have items that report being mapped, but with 'no map' - warn about those.
			noMapParts.add(partNum);
			cpm.reportMapIssue(partNum, getCurrentLineNum(), Severity.LOW, "Map line flagged No Map", status);
		} else if (status.equals(STATUS_ACCEPTED) || (allowStatusMapped && status.equals(STATUS_MAPPED))) {
			processAcceptedMapItem(items, partsSeen, partNum);
		} else if (!status.equals(STATUS_UNMAPPED)) {
			cpm.reportMapIssue(partNum, getCurrentLineNum(), Severity.MEDIUM, "Unusable map status", status);
		}
	}

	private void processAcceptedMapItem(String[] items, Set<String> partsSeen, String partNum) throws TermServerScriptException {
		partsSeen.add(partNum);
		Concept attributeValue;
		try {
			attributeValue = gl.getConcept(items[ColIdx.idx(COL_TARGET)], false, true);
		} catch (TermServerScriptException e) {
			String errMsg = e.getMessage();
			if (errMsg == null || !errMsg.startsWith("Unable")) {
				throw e;
			}
			cpm.reportMapIssue(partNum, getCurrentLineNum(), Severity.HIGH, "Map target not found", errMsg);
			cpm.incrementMapStat(STAT_TARGET_NOT_FOUND);
			return;
		}

		attributeValue = replaceValueIfRequired(partNum, attributeValue);
		if (!attributeValue.isActiveSafely()) {
			//Inactive with no replacement, already reported.  We can't use it, so the part stays unmapped
			return;
		}
		partToAttributeValueMap.computeIfAbsent(partNum, k -> new ArrayList<>()).add(attributeValue);
	}

	/**
	 * @param source the part (or other identifier) mapped to the value, for reporting.  May be null.
	 * @return the replacement for an inactive value, or the value unchanged if it's active or has no replacement
	 */
	public Concept replaceValueIfRequired(String source, Concept attributeValue) {
		if (attributeValue.isActiveSafely()) {
			return attributeValue;
		}
		Concept replacementValue = findReplacement(source, attributeValue, "value");
		cpm.incrementMapStat(replacementValue == null ? STAT_VALUE_NO_REPLACEMENT : STAT_VALUE_REPLACED);
		return replacementValue == null ? attributeValue : replacementValue;
	}

	/**
	 * @param source the part (or other identifier) mapped to the type, for reporting.  May be null.
	 * @return the replacement for an inactive type, or the type unchanged if it's active or has no replacement
	 */
	public Concept replaceTypeIfRequired(String source, Concept attributeType) {
		if (hardCodedTypeReplacementMap.containsKey(attributeType)) {
			attributeType = hardCodedTypeReplacementMap.get(attributeType);
		}
		if (attributeType.isActiveSafely()) {
			return attributeType;
		}
		Concept replacementType = findReplacement(source, attributeType, "type");
		cpm.incrementMapStat(replacementType == null ? STAT_TYPE_NO_REPLACEMENT : STAT_TYPE_REPLACED);
		return replacementType == null ? attributeType : replacementType;
	}

	/**
	 * A map to an inactive concept is out of date: still usable via a replacement (MEDIUM), or no longer viable
	 * without one (HIGH)
	 */
	private Concept findReplacement(String source, Concept inactiveConcept, String role) {
		List<String> notes = new ArrayList<>();
		Concept replacement = knownReplacementMap.get(inactiveConcept);
		String replacedHow = " hard coded replacement ";
		if (replacement == null) {
			replacedHow = " replaced with ";
			replacement = cpm.getReplacementSafely(notes, inactiveConcept, false);
		}

		if (replacement == null) {
			String detail = inactiveConcept + (notes.isEmpty() ? "" : "\n" + String.join("\n", notes));
			cpm.reportMapIssue(source, getCurrentLineNum(), Severity.HIGH, "Mapped to inactive " + role + ", no replacement available", detail);
		} else {
			cpm.reportMapIssue(source, getCurrentLineNum(), Severity.MEDIUM, "Mapped to inactive " + role + ", replacement used", inactiveConcept + replacedHow + replacement);
		}
		return replacement;
	}

	public boolean containsMappingForPartNum(String loincPartNum) {
		return partToAttributeValueMap.containsKey(loincPartNum);
	}

	protected abstract void populateHardCodedMappings() throws TermServerScriptException;

	public void allowStatusMapped(boolean allowStatusMapped) {
		this.allowStatusMapped = allowStatusMapped;
	}
}
