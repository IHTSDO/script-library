package org.ihtsdo.termserver.scripting.fixes.one_offs;

import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import org.ihtsdo.termserver.scripting.template.NormaliseConceptsDriven;

import java.util.*;

public class QI1326_ReplaceAttributeValueInGroup extends BatchFix {

	private static final String MODE_IMFLAMMATION = "409774005 |Inflammatory morphology (morphologic abnormality)|";
	private static final String MODE_EFFUSION = "41699000 |Effusion (morphologic abnormality)|";

	private static final Logger LOGGER = LoggerFactory.getLogger(QI1326_ReplaceAttributeValueInGroup.class);
	private String ecl = "<<65363002 |Otitis media (disorder)| MINUS (<<28371001 |Cholesterol granuloma of middle ear (disorder)| )";
	private RelationshipGroup targetGroup;
	private RelationshipTemplate replaceAttributeTemplate;
	private String mode;
	private List<Concept> verifyAppropriateFindingSite;

	private NormaliseConceptsDriven conceptNormaliser;
	Set<Concept> conceptsNormalised = new HashSet<>();

	protected QI1326_ReplaceAttributeValueInGroup(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		QI1326_ReplaceAttributeValueInGroup fix = new QI1326_ReplaceAttributeValueInGroup(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.populateEditPanel = true;
			fix.populateTaskDescription = true;
			fix.reportNoChange = true;
			fix.selfDetermining = true;
			fix.runStandAlone = true;
			fix.taskPrefix = "QI-1326";
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.configureForSet(MODE_IMFLAMMATION);
			fix.processFile();
			fix.configureForSet(MODE_EFFUSION);
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void configureForSet(String mode) throws TermServerScriptException {
		this.mode = mode;

		if (conceptNormaliser == null) {
			conceptNormaliser = new NormaliseConceptsDriven(this);
		}

		targetGroup = SnomedUtils.createRelationshipGroup(gl,
				new String[][] {
						{"116676008 |Associated morphology (attribute)|",mode},
						{"363698007 |Finding site (attribute)|","119262002 |Ear part (body structure)|"}}
		);
		//Specify an attribute value of null to cause a lookup based on current values
		replaceAttributeTemplate = SnomedUtils.createRelationshipTemplate(gl,"363698007 |Finding site (attribute)| ",null);

		verifyAppropriateFindingSite = new ArrayList<>();
		verifyAppropriateFindingSite.add(gl.getConcept("25342003 |Middle ear structure (body structure)|"));
		verifyAppropriateFindingSite.add(gl.getConcept("53434003 |Right middle ear structure (body structure)|"));
		verifyAppropriateFindingSite.add(gl.getConcept("50460003 |Left middle ear structure (body structure)|"));
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		//We'll set the taskKey to ensure that we only run into a single task despite two runthroughs.
		this.taskKey = t.getKey();
		try {
			Concept loadedConcept = loadConcept(c, t.getBranchPath());
			//Some of these relationships only exist in the inferred form, so we'll need to normalise the concept first
			//But if we process for both Inflammation and Effusion, we don't want to do it twice!
			if (!conceptsNormalised.contains(c)) {
				changesMade = conceptNormaliser.normaliseConcept(t, loadedConcept, null);
				conceptsNormalised.add(c);
			}
			changesMade += switchValues(t, loadedConcept);
			if (changesMade > 0) {
				report(t, c, Severity.NONE, ReportActionType.INFO, c.toExpression(CharacteristicType.STATED_RELATIONSHIP), loadedConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP));
				updateConcept(t, loadedConcept, info);
			}
		} catch (ValidationFailure v) {
			report(t, c, v);
		} catch (Exception e) {
			report(t, c, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}
	
	private int switchValues(Task t, Concept loadedConcept) throws TermServerScriptException {
		int changesMade = 0;
		//Find the group that matches the target, and replace the attribute value
		List<RelationshipGroup> groups = SnomedUtils.findMatchingOrDescendantGroups(loadedConcept, targetGroup, CharacteristicType.STATED_RELATIONSHIP);
		for (RelationshipGroup group : groups) {
			//Find the matching attribute (might be a descendent) via the type
			for (Relationship r : group.getRelationships()) {
				//We need to use local memory equivalents since we've loaded these concepts from the TS
				Concept type = gl.getConcept(r.getType().getId());
				if (type.equals(replaceAttributeTemplate.getType()) ||
						type.getAncestors(NOT_SET).contains(replaceAttributeTemplate.getType())) {
					//BUT it might be that the original attribute had a more specific type, keep that.
					RelationshipTemplate rt = replaceAttributeTemplate.clone();
					rt.setType(r.getType());
					//If we don't have a value, then look it up
					if (rt.getTarget() == null) {
						rt.setTarget(lookupReplacement(r.getTarget()));
					}
					changesMade = replaceRelationship(t, loadedConcept, r, rt);
					break;
				}
			}
		}
		return changesMade;
	}

	private Concept lookupReplacement(Concept existingValue) throws TermServerScriptException {
		if (MODE_IMFLAMMATION.equals(mode)) {
			return switch (existingValue.getId()) {
				case "25342003" -> gl.getConcept("14242009 |Tympanic mucosa structure (body structure)|");
				case "53434003" ->
						gl.getConcept("1381914004 |Structure of tympanic mucosa of right ear (body structure)|");
				case "50460003" ->
						gl.getConcept("1381915003 |Structure of tympanic mucosa of left ear (body structure)|");
				default -> throw new TermServerScriptException(
						"No replacement found for INFLAMMATION mode and finding site: " + existingValue);
			};
		} else if (MODE_EFFUSION.equals(mode)) {
			return switch (existingValue.getId()) {
				case "25342003" -> gl.getConcept("51837005 |Tympanic cavity structure (body structure)|");
				case "53434003" ->
						gl.getConcept("772137004 |Structure of tympanic cavity of right ear (body structure)|");
				case "50460003" ->
						gl.getConcept("772136008 |Structure of tympanic cavity of left ear (body structure)|");
				default -> throw new TermServerScriptException(
						"No replacement found for EFFUSION mode and finding site: " + existingValue);
			};
		} else {
			throw new TermServerScriptException("Unknown morphology mode: " + mode);
		}
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Concept> allAffected = new ArrayList<>();
		LOGGER.info("Identifying concepts to process");
		List<Concept> concepts = SnomedUtils.sort(findConcepts(ecl));

		//We need to verify that our finding site meets expectations

		for (Concept c : concepts) {
			//We want a group that matches the target as descendents, but doesn't _exactly_ match or there is no need to make changes
			List<RelationshipGroup> matchingGroups = SnomedUtils.findMatchingOrDescendantGroups(c, targetGroup, CharacteristicType.INFERRED_RELATIONSHIP);
			for (RelationshipGroup matchingGroup : matchingGroups) {
				if (SnomedUtils.findMatchingGroup(c, targetGroup, CharacteristicType.INFERRED_RELATIONSHIP) == null
						&& verifyAppropriateFindingSite(matchingGroup)) {
					allAffected.add(c);
					break;
				}
			}
		}
		LOGGER.info("Identified {} concepts to process",  allAffected.size());
		allAffected.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<>(allAffected);
	}

	private boolean verifyAppropriateFindingSite(RelationshipGroup matchingGroup) {
		//Find the finding site in this group and check it's appropriate
		Concept thisGroupFindingSite = matchingGroup.getValueForType(FINDING_SITE);
		return verifyAppropriateFindingSite.contains(thisGroupFindingSite);
	}
}
