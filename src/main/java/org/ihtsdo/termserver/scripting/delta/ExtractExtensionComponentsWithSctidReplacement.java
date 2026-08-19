package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.domain.AxiomEntry;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ExtractExtensionComponentsWithSctidReplacement extends ExtractExtensionComponents {

	private static final Logger LOGGER = LoggerFactory.getLogger(ExtractExtensionComponentsWithSctidReplacement.class);

	private Map<String,String> sctIdReplacmentMap = new HashMap<>();
	public static void main(String[] args) throws TermServerScriptException {
		new ExtractExtensionComponentsWithSctidReplacement().doComponentExtraction(args);
	}

	@Override
	public void postInit(String googleFolder) throws TermServerScriptException {
		String[] columnHeadings = new String[]{
				"SCTID, FSN, SemTag, Severity, Action, Details," + additionalReportColumns,
				"ORIG SCTID, FSN, NEW SCTID"
		};

		String[] tabNames = new String[]{
				"Delta Records Created",
				"SCTIDS mapped to new Namespace"

		};
		initialiseSummaryInformation("Unexpected dependencies included");
		super.postInit(googleFolder, tabNames, columnHeadings);
	}

	@Override
	protected void preProcessConcepts(List<Component> componentsOfInterest, boolean viaReview) throws TermServerScriptException {
		//We're going to populate a map of replacement SCTIDs, so that if one concepts references another, we
		//can know what they're going to be replaced by ahead of time
		for (Component component : componentsOfInterest) {
			if (component instanceof Concept concept) {
				sctIdReplacmentMap.put(concept.getConceptId(), conIdGenerator.getSCTID());
			}
		}
		LOGGER.info("Prepared to replaced {} concept SCTIDs into new namespace", sctIdReplacmentMap.size());
	}

	@Override
	protected boolean outputRF2(Concept c, boolean checkAllComponents) throws TermServerScriptException {
		replaceSCTIDs(c);
		return super.outputRF2(c, checkAllComponents);
	}

	private void replaceSCTIDs(Concept c) throws TermServerScriptException {
		//What concept SCTID are we using here?
		String origSctId = c.getConceptId();
		String conceptSctId =  sctIdReplacmentMap.get(origSctId);
		c.setId(conceptSctId);
		for (Description d : c.getDescriptions(ActiveState.BOTH)) {
			d.setConceptId(conceptSctId);
			String newDescId = descIdGenerator.getSCTID();
			for (LangRefsetEntry l : d.getLangRefsetEntries()) {
				l.setReferencedComponentId(newDescId);
			}
		}

		for (AxiomEntry a : c.getAxiomEntries()) {
			a.setReferencedComponentId(conceptSctId);
			String axiomStr = a.getOwlExpression();
			a.setOwlExpression(replaceAllRelevantConceptSctIds(c, axiomStr));
		}
		report(SECONDARY_REPORT, c, conceptSctId);
	}

	private String replaceAllRelevantConceptSctIds(Concept c, String axiomStr) {
		//Work through all mapped replacements
		for (Map.Entry<String, String> entry : sctIdReplacmentMap.entrySet()) {
			String beforeStr = axiomStr;
			axiomStr = axiomStr.replace(entry.getKey(), entry.getValue());
			if (!beforeStr.equals(axiomStr)) {
				LOGGER.info("Replaced {} with {} in axiom for {}", entry.getValue(), entry.getKey(), c);
			}
		}
		return axiomStr;
	}
}
