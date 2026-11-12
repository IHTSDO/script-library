package org.ihtsdo.termserver.scripting.util;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.owltoolkit.conversion.AxiomRelationshipConversionService;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;

/**
 * An extension may add a secondary axiom to a concept it doesn't own, so that its more specific modelling
 * (eg an additional parent) exists in parallel without modifying the owning module's axiom.  When promoting
 * such a concept, this helper merges the secondary axiom(s) into the axiom already on the target server, so
 * only that (modified) axiom is promoted.  Any parent made redundant by a newly added parent is removed.
 */
public class SecondaryAxiomHelper implements ScriptConstants {

	private static final int GROUP_0 = 0;

	private final TermServerScript script;
	private final AxiomRelationshipConversionService axiomService;
	private final GraphLoader gl;

	public SecondaryAxiomHelper(TermServerScript script, AxiomRelationshipConversionService axiomService) {
		this.script = script;
		this.axiomService = axiomService;
		this.gl = script.getGraphLoader();
	}

	/**
	 * @return true if one or more secondary axioms were merged into the concept's existing axiom
	 */
	public boolean mergeSecondaryAxioms(Concept c, Concept conceptOnTS) throws TermServerScriptException {
		List<AxiomEntry> existingAxioms = new ArrayList<>();
		List<AxiomEntry> secondaryAxioms = new ArrayList<>();
		for (AxiomEntry a : c.getAxiomEntries(ActiveState.ACTIVE, false)) {
			if (conceptOnTS.getClassAxiom(a.getId()) != null) {
				existingAxioms.add(a);
			} else if (a.isDirty()) {
				secondaryAxioms.add(a);
			}
		}

		if (existingAxioms.isEmpty() || secondaryAxioms.isEmpty()) {
			return false;
		}

		if (existingAxioms.size() > 1) {
			script.report(c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Multiple axioms already on server, so unable to merge secondary axiom(s)", axiomsToString(secondaryAxioms));
			return false;
		}

		AxiomEntry existingAxiom = existingAxioms.get(0);
		String existingOwl = existingAxiom.getOwlExpression();
		AxiomRepresentation merged = toRepresentation(c, existingAxiom);
		Map<Integer, List<org.snomed.otf.owltoolkit.domain.Relationship>> mergedGroups = copyGroups(merged);
		Set<Long> parentsAdded = new HashSet<>();

		for (AxiomEntry secondaryAxiom : secondaryAxioms) {
			AxiomRepresentation secondary = toRepresentation(c, secondaryAxiom);
			if (!merged.isPrimitive() || !secondary.isPrimitive()) {
				script.report(c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Secondary axiom merged where one or both axioms are fully defined, so the definition changes", secondaryAxiom.getOwlExpression());
			}
			parentsAdded.addAll(addRelationships(mergedGroups, secondary.getRightHandSideRelationships()));
			retireSecondaryAxiom(c, existingAxiom, secondaryAxiom);
		}

		removeRedundantParents(c, existingAxiom, mergedGroups.get(GROUP_0), parentsAdded);
		merged.setRightHandSideRelationships(mergedGroups);

		existingAxiom.setOwlExpression(toOwl(c, merged));
		existingAxiom.setEffectiveTime(null);
		existingAxiom.setDirty();
		script.report(c, Severity.MEDIUM, ReportActionType.AXIOM_CHANGE_MADE, "Secondary axiom(s) merged into axiom already on server: " + existingAxiom.getId(), existingOwl + "\n->\n" + existingAxiom.getOwlExpression());
		return true;
	}

	private AxiomRepresentation toRepresentation(Concept c, AxiomEntry a) throws TermServerScriptException {
		try {
			AxiomRepresentation representation = axiomService.convertAxiomToRelationships(a.getOwlExpression());
			if (representation == null) {
				throw new TermServerScriptException("No axiom representation for " + c + " : " + a.getOwlExpression());
			}
			return representation;
		} catch (ConversionException e) {
			throw new TermServerScriptException("Failed to convert axiom for " + c, e);
		}
	}

	private String toOwl(Concept c, AxiomRepresentation representation) throws TermServerScriptException {
		try {
			return axiomService.convertRelationshipsToAxiom(representation);
		} catch (ConversionException e) {
			throw new TermServerScriptException("Failed to convert merged axiom for " + c, e);
		}
	}

	private Map<Integer, List<org.snomed.otf.owltoolkit.domain.Relationship>> copyGroups(AxiomRepresentation representation) {
		Map<Integer, List<org.snomed.otf.owltoolkit.domain.Relationship>> groups = new TreeMap<>();
		if (representation.getRightHandSideRelationships() != null) {
			representation.getRightHandSideRelationships().forEach((groupId, rels) -> groups.put(groupId, new ArrayList<>(rels)));
		}
		groups.computeIfAbsent(GROUP_0, k -> new ArrayList<>());
		return groups;
	}

	/**
	 * Ungrouped relationships are added to group 0, unless already present.  Role groups are added as new groups,
	 * unless an identical group already exists.
	 * @return the parents added
	 */
	private Set<Long> addRelationships(Map<Integer, List<org.snomed.otf.owltoolkit.domain.Relationship>> mergedGroups,
			Map<Integer, List<org.snomed.otf.owltoolkit.domain.Relationship>> additionalGroups) {
		Set<Long> parentsAdded = new HashSet<>();
		if (additionalGroups == null) {
			return parentsAdded;
		}
		for (Map.Entry<Integer, List<org.snomed.otf.owltoolkit.domain.Relationship>> entry : additionalGroups.entrySet()) {
			if (entry.getKey() == GROUP_0) {
				parentsAdded.addAll(addToGroup0(mergedGroups.get(GROUP_0), entry.getValue()));
			} else if (!containsGroup(mergedGroups, entry.getValue())) {
				addAsNewGroup(mergedGroups, entry.getValue());
			}
		}
		return parentsAdded;
	}

	/**
	 * @return the parents added
	 */
	private Set<Long> addToGroup0(List<org.snomed.otf.owltoolkit.domain.Relationship> group0, List<org.snomed.otf.owltoolkit.domain.Relationship> rels) {
		Set<Long> parentsAdded = new HashSet<>();
		Set<String> existingKeys = toKeys(group0);
		for (org.snomed.otf.owltoolkit.domain.Relationship r : rels) {
			if (existingKeys.add(toKey(r))) {
				group0.add(r);
				if (isParent(r)) {
					parentsAdded.add(r.getDestinationId());
				}
			}
		}
		return parentsAdded;
	}

	private void addAsNewGroup(Map<Integer, List<org.snomed.otf.owltoolkit.domain.Relationship>> mergedGroups, List<org.snomed.otf.owltoolkit.domain.Relationship> rels) {
		int newGroupId = Collections.max(mergedGroups.keySet()) + 1;
		List<org.snomed.otf.owltoolkit.domain.Relationship> newGroup = new ArrayList<>();
		for (org.snomed.otf.owltoolkit.domain.Relationship r : rels) {
			r.setGroup(newGroupId);
			newGroup.add(r);
		}
		mergedGroups.put(newGroupId, newGroup);
	}

	private boolean containsGroup(Map<Integer, List<org.snomed.otf.owltoolkit.domain.Relationship>> groups, List<org.snomed.otf.owltoolkit.domain.Relationship> group) {
		Set<String> groupKeys = toKeys(group);
		return groups.entrySet().stream()
				.filter(e -> e.getKey() != GROUP_0)
				.anyMatch(e -> toKeys(e.getValue()).equals(groupKeys));
	}

	private Set<String> toKeys(List<org.snomed.otf.owltoolkit.domain.Relationship> rels) {
		Set<String> keys = new HashSet<>();
		for (org.snomed.otf.owltoolkit.domain.Relationship r : rels) {
			keys.add(toKey(r));
		}
		return keys;
	}

	private String toKey(org.snomed.otf.owltoolkit.domain.Relationship r) {
		return r.getTypeId() + "=" + (r.isConcrete() ? r.getValueAsString() : String.valueOf(r.getDestinationId()));
	}

	private boolean isParent(org.snomed.otf.owltoolkit.domain.Relationship r) {
		return !r.isConcrete() && String.valueOf(r.getTypeId()).equals(IS_A.getId());
	}

	/**
	 * The secondary axiom is not promoted.  Its relationships now belong to the existing axiom.
	 */
	private void retireSecondaryAxiom(Concept c, AxiomEntry existingAxiom, AxiomEntry secondaryAxiom) {
		for (Relationship r : c.getRelationships(secondaryAxiom)) {
			r.setAxiomEntry(existingAxiom);
		}
		c.getAxiomEntries().remove(secondaryAxiom);
	}

	/**
	 * A parent is redundant if one of the newly added parents is its descendant
	 */
	private void removeRedundantParents(Concept c, AxiomEntry existingAxiom, List<org.snomed.otf.owltoolkit.domain.Relationship> group0, Set<Long> parentsAdded) throws TermServerScriptException {
		Iterator<org.snomed.otf.owltoolkit.domain.Relationship> iterator = group0.iterator();
		while (iterator.hasNext()) {
			org.snomed.otf.owltoolkit.domain.Relationship r = iterator.next();
			if (isParent(r) && !parentsAdded.contains(r.getDestinationId())) {
				Concept parent = gl.getConcept(r.getDestinationId());
				Concept moreSpecificParent = findDescendantParent(parent, parentsAdded);
				if (moreSpecificParent != null) {
					iterator.remove();
					removeStatedParent(c, existingAxiom, parent);
					script.report(c, Severity.MEDIUM, ReportActionType.RELATIONSHIP_DELETED, "Redundant parent removed from merged axiom: " + parent, "Ancestor of added parent " + moreSpecificParent);
				}
			}
		}
	}

	private Concept findDescendantParent(Concept parent, Set<Long> parentsAdded) throws TermServerScriptException {
		for (Long parentAddedId : parentsAdded) {
			Concept parentAdded = gl.getConcept(parentAddedId);
			if (parentAdded.getAncestors(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, false).contains(parent)) {
				return parentAdded;
			}
		}
		return null;
	}

	private void removeStatedParent(Concept c, AxiomEntry existingAxiom, Concept parent) {
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, parent, ActiveState.ACTIVE)) {
			if (existingAxiom.equals(r.getAxiomEntry())) {
				c.removeRelationship(r, true);
			}
		}
	}

	private String axiomsToString(List<AxiomEntry> axioms) {
		StringBuilder sb = new StringBuilder();
		for (AxiomEntry a : axioms) {
			if (!sb.isEmpty()) {
				sb.append("\n");
			}
			sb.append(a.getId()).append(": ").append(a.getOwlExpression());
		}
		return sb.toString();
	}
}
