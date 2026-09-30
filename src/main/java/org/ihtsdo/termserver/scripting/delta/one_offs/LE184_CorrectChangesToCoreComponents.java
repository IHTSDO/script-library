package org.ihtsdo.termserver.scripting.delta.one_offs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.delta.DeltaGeneratorWithAutoImport;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ComponentAnnotationEntry;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import java.util.Arrays;

/**
 * LE-184 Components of promoted LOINC concepts were modified (mostly inactivated) without changing their module,
 * so they sit unpublished in the core module and the release build - which only extracts the LOINC module - drops them.
 * Move those components into the LOINC module.  The exception is Component Annotations, which are an International
 * feature we don't want to touch, so revert those to their previously published state.  Their effective time is
 * output blank; Snowstorm restores the published effective time on import when the state matches the release hash.
 */
public class LE184_CorrectChangesToCoreComponents extends DeltaGeneratorWithAutoImport {

	private static final String LOINC_NAMESPACE = "1010000";

	public static void main(String[] args) throws TermServerScriptException {
		new LE184_CorrectChangesToCoreComponents().standardExecution(args);
	}

	@Override
	public void init(String[] args) throws TermServerScriptException {
		super.init(args);
		taskPrefix = "LE-184";
		getSnapshotConfiguration().setPopulateReleaseFlag(true);
		getSnapshotConfiguration().setLoadOtherReferenceSets(true);
		getGraphLoader().setRecordPreviousState(true);  //Needed to revert the annotations
	}

	@Override
	public void process() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			boolean changesMade = false;
			for (Component comp : SnomedUtils.getAllComponents(c)) {
				if (!StringUtils.isEmpty(comp.getEffectiveTime())
						|| !comp.getModuleId().equals(SCTID_CORE_MODULE)) {
					continue;
				}

				if (!isLoincNamespace(c)) {
					report(c, Severity.HIGH, ReportActionType.SKIPPING, comp.getComponentType(), comp, "Core component in delta on a concept not in the LOINC namespace");
				} else if (comp instanceof ComponentAnnotationEntry cae) {
					changesMade |= revertAnnotation(c, cae);
				} else {
					comp.setModuleId(SCTID_LOINC_EXTENSION_MODULE);
					comp.setDirty();
					changesMade = true;
					report(c, Severity.LOW, ReportActionType.MODULE_CHANGE_MADE, comp.getComponentType(), comp);
				}
			}

			if (changesMade) {
				outputRF2(c, true);  //Will only output dirty components
				recordConceptWritten();
			}
		}
	}

	private boolean revertAnnotation(Concept c, ComponentAnnotationEntry cae) throws TermServerScriptException {
		if (!cae.hasPreviousStateDataRecorded()) {
			report(c, Severity.HIGH, ReportActionType.SKIPPING, cae.getComponentType(), cae, "No previous state recorded - was this annotation ever published?");
			return false;
		}

		//revertToPreviousState only restores active and module, so check nothing else has changed
		//Previous state starts with active, module; the remaining fields must be as published
		String[] previous = cae.getPreviousState();
		String[] current = cae.getMutableFields();
		if (!Arrays.equals(previous, 2, previous.length, current, 2, current.length)) {
			report(c, Severity.HIGH, ReportActionType.SKIPPING, cae.getComponentType(), cae, "Fields other than active/module differ from published: " + String.join(",", previous));
			return false;
		}

		String problemState = cae.toString();
		cae.revertToPreviousState();
		cae.setEffectiveTime(null);  //Snowstorm restores the published effective time on import
		cae.setDirty();
		report(c, Severity.LOW, ReportActionType.COMPONENT_REVERTED, cae.getComponentType(), cae, "Was: " + problemState);
		return true;
	}

	private boolean isLoincNamespace(Concept c) {
		return SnomedUtilsBase.isSctid(c.getId())
				&& LOINC_NAMESPACE.equals(SnomedUtilsBase.getNamespace(c.getId()));
	}
}
