package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.pipeline.AbstractMapManager;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads a Snap2SNOMED-format map file where column 1 (Source code) is a LoincTerm's
 * LOINC number, rather than a LoincPart number as used by AttributePartMapManager,
 * producing a single Concept per LoincTerm rather than a list of attribute values.
 */
public class TermConceptMapManager extends AbstractMapManager implements LoincScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(TermConceptMapManager.class);

	private static final boolean ALLOW_DRAFT_MAPPINGS = true;

	private static final String COL_RELATIONSHIP_TYPE = "Relationship type code";
	private static final String STATUS_ACCEPTED = "ACCEPTED";
	private static final String STATUS_DRAFT = "DRAFT";
	private static final String TARGET_EQUIVALENT = "TARGET_EQUIVALENT";

	private final LoincScript ls;
	private final Map<LoincTerm, Concept> conceptMap = new HashMap<>();

	public TermConceptMapManager(LoincScript ls) {
		super(ls);
		this.ls = ls;
	}

	public Map<LoincTerm, Concept> getConceptMap() {
		return conceptMap;
	}

	public void populateConceptMap(File mapFile) throws TermServerScriptException {
		loadMapFile(mapFile, this::processMapFileLine);
		removeSharedTargets();
		LOGGER.info("Populated map of {} terms to concepts", conceptMap.size());
	}

	private void processMapFileLine(String[] items) throws TermServerScriptException {
		String loincNum = items[ColIdx.idx(COL_PART_NUM)];
		String status = items[ColIdx.idx(COL_STATUS)];

		if (items[ColIdx.idx(COL_NO_MAP)].equals("true") || !isStatusAllowed(status)) {
			cpm.incrementSummaryCount(MAP_IMPORT, "Skipped - status " + status);
			return;
		}

		String relationshipType = items[ColIdx.idx(COL_RELATIONSHIP_TYPE)];
		if (!relationshipType.equals(TARGET_EQUIVALENT)) {
			cpm.incrementSummaryCount(MAP_IMPORT, "Skipped - " + relationshipType);
			return;
		}

		//Thrown exceptions are reported on the Map Issues tab
		LoincTerm loincTerm = ls.getLoincTerm(loincNum);
		if (loincTerm == null) {
			throw new TermServerScriptException("Term listed in map but not known to LOINC terms file");
		}

		if (!LOINC_CLASSTYPE_CLINICAL.equals(loincTerm.getClassType())) {
			throw new TermServerScriptException("Term has CLASSTYPE " + loincTerm.getClassType() + ", not clinical (" + LOINC_CLASSTYPE_CLINICAL + ")");
		}

		String targetId = items[ColIdx.idx(COL_TARGET)];
		Concept concept = gl.getConcept(targetId, false, false);
		if (concept == null || !concept.isActiveSafely()) {
			throw new TermServerScriptException("Target " + targetId + " is missing or inactive");
		}
		conceptMap.put(loincTerm, concept);
	}

	private boolean isStatusAllowed(String status) {
		return status.equals(STATUS_ACCEPTED) || (ALLOW_DRAFT_MAPPINGS && status.equals(STATUS_DRAFT));
	}

	private void removeSharedTargets() throws TermServerScriptException {
		//See LE-189.  Where several terms are equivalent to the same concept, we can't say which is right
		Map<Concept, List<LoincTerm>> termsByConcept = conceptMap.entrySet().stream()
				.collect(Collectors.groupingBy(Map.Entry::getValue,
						Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

		for (Map.Entry<Concept, List<LoincTerm>> entry : termsByConcept.entrySet()) {
			if (entry.getValue().size() > 1) {
				String loincNums = entry.getValue().stream()
						.map(LoincTerm::getLoincNum)
						.sorted()
						.collect(Collectors.joining(", "));
				for (LoincTerm loincTerm : entry.getValue()) {
					cpm.report(cpm.getTab(TAB_MAP_ISSUES), "", loincTerm.getLoincNum(),
							"Skipped (LE-189): " + loincNums + " all equivalent to " + entry.getKey());
					cpm.incrementSummaryCount(MAP_IMPORT, "Skipped - shared target");
					conceptMap.remove(loincTerm);
				}
			}
		}
	}

}
