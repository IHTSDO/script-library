package org.ihtsdo.termserver.scripting.pipeline.loinc;

import org.ihtsdo.otf.exception.TermServerScriptException;

public class ImportClinicalLoincTerms extends ImportLoincTerms {

	TermConceptMapManager termConceptMapManager;

	public static void main(String[] args) throws TermServerScriptException {
		new ImportClinicalLoincTerms().ingestExternalContent(args);
	}

	@Override
	protected void loadSupportingInformation() throws TermServerScriptException {
		super.loadSupportingInformation();
		loadClinicalConceptMap();
	}

	private void loadClinicalConceptMap() throws TermServerScriptException {
		termConceptMapManager = new TermConceptMapManager(this);
		termConceptMapManager.populateConceptMap(getInputFile(FILE_IDX_LOINC_CONCEPT_MAP_FILE));
	}

}
