package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.template.NormaliseConcepts;
import org.ihtsdo.termserver.scripting.util.ConceptLateralizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ExtractExtensionComponentsAndLateralize extends ExtractExtensionComponents {

	private static final Logger LOGGER = LoggerFactory.getLogger(ExtractExtensionComponentsAndLateralize.class);

	private ConceptLateralizer conceptLateralizer = null;

	private NormaliseConcepts conceptNormalizer = null;

	public static void main(String[] args) throws TermServerScriptException {
		new ExtractExtensionComponentsAndLateralize().doComponentExtraction(args);
	}

	@Override
	public void postInit(String googleFolder) throws TermServerScriptException {
		//Ensure the Report Manager is initialised before we create helper classes, so they receive the same object
		super.postInit(googleFolder);
		conceptLateralizer = ConceptLateralizer.get(this, copyInferredRelationshipsToStatedWhereMissing, null);
		conceptNormalizer = new NormaliseConcepts(this);
	}

	@Override
	protected void doAdditionalProcessing(Concept c, List<Component> componentsToProcess) throws TermServerScriptException {
		//Now it might be that the source extension already has this concept lateralized
		//eg 847081000000101 |Balloon dilatation of bronchus using fluoroscopic guidance (procedure)|
		LOGGER.info("Creating lateralized concepts for {}", c);
		if (copyInferredRelationshipsToStatedWhereMissing)	{
			conceptNormalizer.normaliseConcept(null, c, null);
		}

		try {
			conceptLateralizer.createLateralizedConceptIfRequired(c, LEFT, componentsToProcess);
			conceptLateralizer.createLateralizedConceptIfRequired(c, RIGHT, componentsToProcess);
			conceptLateralizer.createLateralizedConceptIfRequired(c, BILATERAL, componentsToProcess);
		} catch (TermServerScriptException e) {
			report(c, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to create lateralized concepts for " + c + " due to: " + e.getMessage());
		}
	}

}