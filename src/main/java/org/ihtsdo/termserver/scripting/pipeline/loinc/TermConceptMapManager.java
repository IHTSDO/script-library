package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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

	private static final String STATUS_ACCEPTED = "ACCEPTED";

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
		LOGGER.info("Populated map of {} terms to concepts", conceptMap.size());
	}

	private void processMapFileLine(String[] items) throws TermServerScriptException {
		String loincNum = items[ColIdx.idx(COL_PART_NUM)];

		if (items[ColIdx.idx(COL_NO_MAP)].equals("true") || !items[ColIdx.idx(COL_STATUS)].equals(STATUS_ACCEPTED)) {
			return;
		}

		LoincTerm loincTerm = ls.getLoincTerm(loincNum);
		if (loincTerm == null) {
			LOGGER.warn("Term {} listed in map but not known to LOINC terms file", loincNum);
			return;
		}

		Concept concept = gl.getConcept(items[ColIdx.idx(COL_TARGET)], false, true);
		conceptMap.put(loincTerm, concept);
	}

}
