package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.pipeline.AttributePartMapManager;
import org.ihtsdo.termserver.scripting.pipeline.domain.Part;

public class LoincAttributePartMapManager extends AttributePartMapManager implements LoincScriptConstants {

	public LoincAttributePartMapManager (LoincScript ls, Map<String, Part> partMap, Map<String, String> partMapNotes) {
		super(ls, partMap, partMapNotes);
	}

	protected void populateConceptReplacements() throws TermServerScriptException {
		knownReplacementMap.put(gl.getConcept("720309005 |Immunoglobulin G antibody to Streptococcus pneumoniae 43 (substance)|"), gl.getConcept("767402003 |Immunoglobulin G antibody to Streptococcus pneumoniae Danish serotype 43 (substance)|"));
		knownReplacementMap.put(gl.getConcept("720308002 |Immunoglobulin G antibody to Streptococcus pneumoniae 34 (substance)|"), gl.getConcept("767408004 |Immunoglobulin G antibody to Streptococcus pneumoniae Danish serotype 34 (substance)|"));
		knownReplacementMap.put(gl.getConcept("54708003 |Extended zinc insulin (substance)|"), gl.getConcept("10329000 |Zinc insulin (substance)|"));
		knownReplacementMap.put(gl.getConcept("409258004 |Hydroxocobalamin (substance)|"), gl.getConcept("1217427007 |Aquacobalamin (substance)|"));
		knownReplacementMap.put(gl.getConcept("301892007 |Biopterin analyte (substance)|"), gl.getConcept("1231481007 |Substance with biopterin structure (substance)|"));
		knownReplacementMap.put(gl.getConcept("301892007 |Biopterin analyte (substance)|"), gl.getConcept("1231481007 |Substance with biopterin structure (substance)|"));
		knownReplacementMap.put(gl.getConcept("27192005 |Aminosalicylic acid (substance)|"), gl.getConcept("255666002 |Para-aminosalicylic acid (substance)|"));
		knownReplacementMap.put(gl.getConcept("250428009 |Substance with antimicrobial mechanism of action (substance)|"), gl.getConcept("419241000 |Substance with antibacterial mechanism of action (substance)|"));
		knownReplacementMap.put(gl.getConcept("119306004 |Drain device specimen (specimen)|"), gl.getConcept("1003707004 |Drain device submitted as specimen (specimen)|"));

		hardCodedTypeReplacementMap.put(gl.getConcept("410670007 |Time|"), gl.getConcept("370134009 |Time aspect|"));
	}

	@Override
	public boolean containsMappingForPartNum(String loincPartNum) {
		return partToAttributeValueMap.containsKey(loincPartNum);
	}

	protected void populateHardCodedMappings() throws TermServerScriptException {
		/*hardCodedMappings.put("LP36683-8", List.of(
				gl.getConcept("106202009 |Antigen in ABO blood group system (substance)|"),
				gl.getConcept("16951006 |Antigen in Rh blood group system (substance)")));
		hardCodedMappings.put("LP15445-7", List.of(
				gl.getConcept("259498006 |Bilirubin glucuronide (substance)|"),
				gl.getConcept("73828001 |Bilirubin-albumin complex (substance)")));
		hardCodedMappings.put("LP182450-9", List.of(
				gl.getConcept("259337002 |Calcifediol (substance"),
				gl.getConcept("67517005 |25-hydroxyergocalciferol (substance)")));*/
		hardCodedMappings.put("LP447904-6", List.of(
				gl.getConcept("685451010000100 |Measurement property (qualifier value)|")));
	}

}
