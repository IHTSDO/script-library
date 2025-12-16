package org.ihtsdo.termserver.scripting.pipeline.npu.template;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.pipeline.*;
import org.ihtsdo.termserver.scripting.pipeline.domain.*;
import org.ihtsdo.termserver.scripting.pipeline.npu.NpuScriptConstants;
import org.ihtsdo.termserver.scripting.pipeline.npu.domain.NpuConcept;
import org.ihtsdo.termserver.scripting.pipeline.npu.domain.NpuDetail;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConcept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class NpuTemplatedConcept extends TemplatedConcept implements NpuScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(NpuTemplatedConcept.class);

	private static Concept unitsAttribute;
	private static RelationshipTemplate defaultUnitOfMeasureAttribute;
	private static RelationshipTemplate fastingPreconditionAttribute;
	private static Map<String, NpuDetail> npuDetailMap;

	private static final String TRAILING_IN = " in ";

	private static final List<String> PLACEHOLDER_ELEMENTS = List.of("QU70500", "QU58906");

	public static void initialise(ContentPipelineManager cpm,
	                              Map<String, NpuDetail> npuDetailMap) throws TermServerScriptException {
		TemplatedConcept.initialise(cpm);
		NpuTemplatedConcept.npuDetailMap = npuDetailMap;
		Concept unitOfMeasure = gl.getConcept("767524001 |Unit of measure| ");
		unitsAttribute = gl.getConcept("246514001 |Units|");
		defaultUnitOfMeasureAttribute = new RelationshipTemplate(unitsAttribute, unitOfMeasure);

		Concept precondition = gl.getConcept("704326004 |Precondition|");
		Concept fasting = gl.getConcept("726055006 |After calorie fasting (qualifier value)|");
		fastingPreconditionAttribute = new RelationshipTemplate(precondition, fasting);
	}

		@Override
	public String getSchemaId() {
		return SCTID_NPU_SCHEMA;
	}

	@Override
	public String getSemTag() {
		return " (observable entity)";
	}

	protected NpuTemplatedConcept(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	protected void applyTemplateSpecificRules(List<RelationshipTemplate> attributes, RelationshipTemplate rt) throws TermServerScriptException {
		//Do we need to apply any specific rules to the modeling?
		//Override this function if so
	}
	
	protected NpuConcept getNpuConcept() {
		return (NpuConcept)externalConcept;
	}

	protected void populateTypeMapCommonItems() throws TermServerScriptException {
		typeMap.put(NPU_PART_COMPONENT, gl.getConcept("246093002 |Component (attribute)|"));
		typeMap.put(NPU_PART_PROPERTY, gl.getConcept("370130000 |Property (attribute)|"));
		typeMap.put(NPU_PART_SCALE, gl.getConcept("370132008 |Scale type (attribute)|"));
		typeMap.put(NPU_PART_SYSTEM, gl.getConcept("704319004 |Inheres in|"));
		typeMap.put(NPU_PART_UNIT, unitsAttribute);
	}

	@Override
	protected void populateParts() throws TermServerScriptException {
		prepareConceptDefaultedForModule(SCTID_NPU_EXTENSION_MODULE);
		NpuConcept npuConcept = getNpuConcept();
		NpuDetail npuDetail = npuDetailMap.get(getExternalIdentifier());
		for (Part part : npuDetail.getParts(npuConcept)) {
			populatePart(normalise(part));
		}

		//Ensure attributes are unique (considering both type and value)
		checkAndRemoveDuplicateAttributes();
	}

	private Part normalise(Part part) {
		//Now if the partNum contains a placeholder, we'll strip that out
		String partNum = part.getPartNumber();
		for (String placeholder : PLACEHOLDER_ELEMENTS) {
			if (partNum.contains(placeholder)) {
				partNum = partNum.replace(placeholder, "").trim();
				if (partNum.endsWith(",")) {
					partNum = partNum.substring(0, partNum.length() - 1);
				}
				break;
			}
		}
		Part normalisedPart = part.clone();
		normalisedPart.setPartNumber(partNum);
		return normalisedPart;
	}

	@Override
	protected boolean addAttributeFromDetailWithType(List<RelationshipTemplate> attributes, Part part, Concept attributeType) throws TermServerScriptException {
		List<RelationshipTemplate> additionalAttributes = cpm.getAttributePartManager().getPartMappedAttributeForType(this, part.getPartNumber(), attributeType);
		//Did we fail to find a value when using a compound key?  Try the individual parts of they key if so, and mark the concept as primitive
		if (part.getPartNumber().contains(",") && (additionalAttributes == null || additionalAttributes.isEmpty())) {
			additionalAttributes = attemptPartialCompoundKeyLookup(part, attributeType);
		}
		for (RelationshipTemplate rt : new ArrayList<>(additionalAttributes)) {
			applyTemplateSpecificModellingRules(additionalAttributes, part, rt);
		}
		attributes.addAll(additionalAttributes);
		return !additionalAttributes.isEmpty();
	}

	private List<RelationshipTemplate> attemptPartialCompoundKeyLookup(Part part, Concept attributeType) throws TermServerScriptException {
		String[] partialKeys = part.getPartNumber().split(",");
		List<RelationshipTemplate> additionalAttributes = cpm.getAttributePartManager().getPartMappedAttributeForType(this, partialKeys[0], attributeType);

		//Did we get one?
		if (additionalAttributes != null && !additionalAttributes.isEmpty()) {
			addProcessingFlag(ProcessingFlag.MARK_AS_PRIMITIVE);
			//Do we also know about the other part?
			Part otherPart = cpm.getPart(partialKeys[1].trim());
			if (otherPart != null) {
				//Check for additional modeling rules relating to this part
				applyTemplateSpecificModellingRules(additionalAttributes, otherPart, null);
				slotTermAppendMap.put(NPU_PART_UNIT, " with reference to " + otherPart.getPartName());
			}
			//In this case we don't need report the compound key as a missing mapping
			cpm.removeMissingMapping(part);
		}
		return additionalAttributes;
	}

	private void populatePart(Part part) throws TermServerScriptException {
		List<RelationshipTemplate> attributesToAdd = new ArrayList<>();
		addAttributeFromDetail(attributesToAdd, part);

		if (part.getPartTypeName().equals(NPU_PART_UNIT)
				&& SnomedUtils.isEmpty(part.getPartNumber())) {
			//NPU Concepts without a unit will be given a default unit of measure
			attributesToAdd.add(defaultUnitOfMeasureAttribute);
			slotTermMap.put(NPU_PART_UNIT, "");
		}

		for (RelationshipTemplate rt : attributesToAdd) {
			addAttributesToConcept(rt, part, false);
		}
	}

	@Override
	protected boolean detailsIndicatePrimitiveConcept() throws TermServerScriptException {
		return false;
	}

	@Override
	protected void applyTemplateSpecificModellingRules(List<RelationshipTemplate> attributes, Part part, RelationshipTemplate rt) throws TermServerScriptException {
		//If we're working with a Unit, and we haven't found a mapping for it, then use the name from the NPU part
		//and make the concept primitive
		if (part != null && part.getPartTypeName() == null) {
			LOGGER.warn("Part here without a part type? {}", part);
		} else {
			if (part.getPartTypeName().equals(NPU_PART_UNIT) && attributes.isEmpty()) {
				addProcessingFlag(ProcessingFlag.MARK_AS_PRIMITIVE);
				slotTermMap.put(NPU_PART_UNIT, part.getPartName());
			}

			if (part.getPartName().equals("System")) {
				slotTermMap.put(NPU_PART_SYSTEM, "");
			}
		}

		if (part.getPartNumber().contains("QU65029")) {
			//Add an extra attribute for a precondition of fasting
			attributes.add(fastingPreconditionAttribute);
		}

	}

	@Override
	protected void applyTemplateSpecificTermingRules(Description d) throws TermServerScriptException {
		//Did we have a blank Unit?  Trim off that trailing "in" if so.
		if (slotTermMap.containsKey(NPU_PART_UNIT)
				&& d.getTerm().endsWith(TRAILING_IN)) {
			if (d.getType().equals(DescriptionType.FSN)) {
				String[] fsnParts = SnomedUtilsBase.deconstructFSN(d.getTerm());
				String newBase = fsnParts[0].substring(0, fsnParts[0].length() - TRAILING_IN.length());
				d.setTerm(newBase + " " + fsnParts[1]);
			} else {
				d.setTerm(d.getTerm().substring(0, d.getTerm().length() - TRAILING_IN.length()));
			}
		}
	}
	
}
