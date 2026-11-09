package org.ihtsdo.termserver.scripting.snapshot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ComponentAnnotationEntry;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.client.TermServerClient.ExportType;
import org.ihtsdo.termserver.scripting.client.TermServerClient.ExtractType;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * RP-272 Reconcile an exported snapshot against a generated one
 * RP-440 The snapshot constructed from the previous published release plus the project's delta is taken
 * as authoritative, and compared with a snapshot exported from the Terminology Server, for the project's
 * own modules.  A delta is output to restore any component that differs from, or is missing from, the
 * Terminology Server, keeping its published effective time.  Components only the Terminology Server holds
 * cannot be removed via RF2, so they are listed on the NEEDS DELETED tab.
 * NB the export is saved to the working directory.  Pass it back in with -f to skip exporting next time.
 */
public class ReconcileSnapshot extends DeltaGenerator {

	private static final int SUMMARY_TAB = PRIMARY_REPORT;
	private static final int NEEDS_DELETED_TAB = 1;
	private static final int OTHER_REFSETS_TAB = 11;
	private static final String[] TAB_NAMES = new String[] {
			"Summary", "NEEDS DELETED", "Concepts", "Descriptions", "Relationships", "Axioms", "Lang Refset",
			"Inactivation Indicators", "Historical Associations", "Alternate Identifiers", "Annotations",
			"Other Refsets"};
	private static final String COMPONENT_HEADINGS = "SCTID, FSN, SemTag, Severity, Action, Component Id, Details, Constructed (published), Exported (Terminology Server)";

	private static final String RUN_DETAILS = "Run details";
	private static final String IN_SCOPE = "In scope in constructed snapshot";
	private static final String MATCHED = "Matched";
	private static final String RESTORED_DIFFERENT = "Different - restored to published state";
	private static final String RESTORED_MISSING = "Missing from Terminology Server - restored";
	private static final String NOT_RESTORED = "Not restored - CRITICAL";
	private static final String NEEDS_DELETED = "Only in Terminology Server - NEEDS DELETED";
	private static final String OUTPUT = "Output";
	private static final String CONCEPTS_WRITTEN = "Concepts written to delta";

	//Files classified as this type hold refset members we only know generically, by the header's field names
	private static final ComponentType GENERIC_REFSET = ComponentType.UNKNOWN;

	private Set<String> inScopeModules;
	private File exportedSnapshot;
	private Map<String, Component> remainingComponents;
	private final Set<Concept> conceptsToOutput = new LinkedHashSet<>();
	//Recorded during init, then written to the Summary tab once it exists
	private final List<String[]> runDetails = new ArrayList<>();

	public static void main(String[] args) throws TermServerScriptException {
		new ReconcileSnapshot().standardExecution(args);
	}

	@Override
	protected void init(String[] args) throws TermServerScriptException {
		super.init(args);
		getSnapshotConfiguration().setLoadOtherReferenceSets(true);
		inScopeModules = new LinkedHashSet<>(getInScopeModules(true));
		if (inScopeModules.isEmpty()) {
			throw new TermServerScriptException("Unable to determine the modules of " + project.getKey() + ".  Check its code system's metadata.");
		}
		runDetails.add(new String[] {RUN_DETAILS, "Project", project.getKey() + " on " + project.getBranchPath()});
		inScopeModules.forEach(m -> runDetails.add(new String[] {RUN_DETAILS, "In scope module", m}));

		//Export now, so it lines up as closely as possible with the delta exported for the constructed snapshot
		if (hasInputFile(0)) {
			exportedSnapshot = getInputFile(0);
			runDetails.add(new String[] {RUN_DETAILS, "Terminology Server snapshot (reused)", exportedSnapshot.getAbsolutePath()});
		} else {
			exportedSnapshot = exportSnapshot();
			runDetails.add(new String[] {RUN_DETAILS, "Terminology Server snapshot (exported)", exportedSnapshot.getAbsolutePath()});
		}
	}

	private File exportSnapshot() throws TermServerScriptException {
		String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
		File snapshot = new File("snapshot_export_" + project.getKey() + "_" + timestamp + ".zip");
		LOGGER.info("Exporting {} snapshot to {} - pass this file with -f to reuse it next time", project.getBranchPath(), snapshot.getAbsolutePath());
		getTSClient().export(project.getBranchPath(), null, ExportType.UNPUBLISHED, ExtractType.SNAPSHOT, snapshot, false);
		return snapshot;
	}

	@Override
	public void postInit(String googleFolder) throws TermServerScriptException {
		summaryTabIdx = SUMMARY_TAB;
		String[] columnHeadings = new String[TAB_NAMES.length];
		Arrays.fill(columnHeadings, COMPONENT_HEADINGS);
		columnHeadings[SUMMARY_TAB] = "Category, Item, Value, , , ";
		super.postInit(GFOLDER_RELEASE_QA, TAB_NAMES, columnHeadings);

		for (String[] detail : runDetails) {
			report(SUMMARY_TAB, (Object[]) detail);
		}
		for (int tabIdx = NEEDS_DELETED_TAB + 1; tabIdx <= OTHER_REFSETS_TAB; tabIdx++) {
			for (String item : new String[] {IN_SCOPE, MATCHED, RESTORED_DIFFERENT, RESTORED_MISSING, NOT_RESTORED, NEEDS_DELETED}) {
				initialiseSummaryCount(TAB_NAMES[tabIdx], item);
			}
		}
		initialiseSummaryCount(OUTPUT, CONCEPTS_WRITTEN);
	}

	@Override
	protected void process() throws TermServerScriptException {
		remainingComponents = new HashMap<>();
		for (Component c : gl.getAllComponents()) {
			if (inScopeModules.contains(c.getModuleId())) {
				remainingComponents.put(c.getId(), c);
				incrementSummaryCount(getTabName(c), IN_SCOPE);
			}
		}
		LOGGER.info("{} components in scope in the constructed snapshot", remainingComponents.size());

		reconcileArchive(exportedSnapshot);
		restoreMissingComponents();

		for (Concept c : conceptsToOutput) {
			if (outputRF2(c, true)) {
				recordConceptWritten();
				incrementSummaryCount(OUTPUT, CONCEPTS_WRITTEN);
			}
		}
		reportSummaryCounts(SUMMARY_TAB);
	}

	private void reconcileArchive(File archive) throws TermServerScriptException {
		LOGGER.info("Reconciling against {}", archive);
		try (ZipInputStream zis = new ZipInputStream(new FileInputStream(archive))) {
			ZipEntry ze = zis.getNextEntry();
			while (ze != null) {
				String fileName = Paths.get(ze.getName()).getFileName().toString();
				if (!ze.isDirectory() && fileName.contains("Snapshot")) {
					reconcileFile(fileName, zis);
				}
				ze = zis.getNextEntry();
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to read " + archive, e);
		}
	}

	private void reconcileFile(String fileName, InputStream is) throws IOException, TermServerScriptException {
		ComponentType componentType = classify(fileName);
		if (componentType == null) {
			LOGGER.info("Skipping {}", fileName);
			return;
		}
		LOGGER.info("Reconciling {} as {}", fileName, componentType);
		//Not using try with resources, as closing the reader would close the zip stream
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		String header = br.readLine();
		String[] fieldNames = header == null ? new String[0] : header.split(FIELD_DELIMITER, -1);
		String[] additionalFieldNames = fieldNames.length > REF_IDX_FIRST_ADDITIONAL
				? Arrays.copyOfRange(fieldNames, REF_IDX_FIRST_ADDITIONAL, fieldNames.length) : new String[0];
		String line;
		while ((line = br.readLine()) != null) {
			String[] lineItems = line.split(FIELD_DELIMITER, -1);
			if (isInScope(lineItems)) {
				reconcile(createComponent(componentType, lineItems, additionalFieldNames));
			}
		}
		flushFilesSoft();
	}

	/**
	 * Mirrors the order in which ArchiveImporter recognises files, so we read each file as the
	 * constructed snapshot did.  Returns null for files the constructed snapshot doesn't hold as components.
	 */
	private ComponentType classify(String fileName) {
		if (fileName.contains("sct2_Concept_")) {
			return ComponentType.CONCEPT;
		} else if (fileName.contains("Identifier")) {
			return ComponentType.ALTERNATE_IDENTIFIER;
		} else if (fileName.contains("sct2_Relationship_") || fileName.contains("sct2_RelationshipConcrete")) {
			return ComponentType.INFERRED_RELATIONSHIP;
		} else if (fileName.contains("sct2_sRefset_OWLExpression") || fileName.contains("sct2_sRefset_OWLAxiom")) {
			return ComponentType.AXIOM;
		} else if (fileName.contains("sct2_Description_") || fileName.contains("sct2_TextDefinition_")) {
			return ComponentType.DESCRIPTION;
		} else if (fileName.contains("sct2_") || fileName.contains("MRCM") || fileName.contains("ssRefset_ModuleDependency")) {
			//Stated relationships come via axioms, and MRCM and module dependencies aren't held as components
			return null;
		}
		return classifyRefset(fileName);
	}

	private ComponentType classifyRefset(String fileName) {
		if (fileName.contains("InactivationIndicatorReferenceSet") || fileName.contains("der2_cRefset_AttributeValue")) {
			return ComponentType.ATTRIBUTE_VALUE;
		} else if (fileName.contains("Association")) {
			return ComponentType.HISTORICAL_ASSOCIATION;
		} else if (fileName.contains("ComponentAnnotationStringValue")) {
			return ComponentType.COMPONENT_ANNOTATION;
		} else if (fileName.contains("English") || fileName.contains("Language")) {
			return ComponentType.LANGREFSET;
		} else if (fileName.contains("Refset")) {
			return GENERIC_REFSET;
		}
		return null;
	}

	private boolean isInScope(String[] lineItems) {
		if (inScopeModules.contains(lineItems[IDX_MODULEID])) {
			return true;
		}
		//A component whose module was changed in the Terminology Server is still one of ours
		Component constructed = gl.getComponent(lineItems[IDX_ID]);
		return constructed != null && inScopeModules.contains(constructed.getModuleId());
	}

	private Component createComponent(ComponentType componentType, String[] lineItems, String[] additionalFieldNames) throws TermServerScriptException {
		switch (componentType) {
			case CONCEPT:
				return Concept.fillFromRf2(new Concept(lineItems[IDX_ID]), lineItems);
			case DESCRIPTION:
				Description d = new Description(lineItems[DES_IDX_ID]);
				Description.fillFromRf2(d, lineItems);
				return d;
			case INFERRED_RELATIONSHIP:
				return createRelationship(lineItems);
			case AXIOM:
				return AxiomEntry.fromRf2(lineItems);
			case ATTRIBUTE_VALUE:
				return InactivationIndicatorEntry.fromRf2(lineItems);
			case HISTORICAL_ASSOCIATION:
				return AssociationEntry.fromRf2(lineItems);
			case LANGREFSET:
				return LangRefsetEntry.fromRf2(lineItems);
			case ALTERNATE_IDENTIFIER:
				AlternateIdentifier altId = new AlternateIdentifier();
				AlternateIdentifier.populatefromRf2(altId, lineItems);
				return altId;
			case COMPONENT_ANNOTATION:
				return ComponentAnnotationEntry.fromRf2(lineItems);
			default:
				RefsetMember rm = new RefsetMember();
				RefsetMember.populatefromRf2(rm, lineItems, additionalFieldNames);
				return rm;
		}
	}

	private Relationship createRelationship(String[] lineItems) throws TermServerScriptException {
		Concept source = lookupConcept(lineItems[REL_IDX_SOURCEID]);
		Concept type = lookupConcept(lineItems[REL_IDX_TYPEID]);
		int groupNum = Integer.parseInt(lineItems[REL_IDX_RELATIONSHIPGROUP]);
		String target = lineItems[REL_IDX_DESTINATIONID];

		Relationship r;
		if (target.startsWith("#")) {
			r = new Relationship(source, type, target.substring(1), groupNum, ConcreteValue.ConcreteValueType.NUMERIC);
		} else if (target.startsWith("\"")) {
			r = new Relationship(source, type, target.substring(1, target.length() - 1), groupNum, ConcreteValue.ConcreteValueType.STRING);
		} else {
			r = new Relationship(source, type, lookupConcept(target), groupNum);
		}
		r.setRelationshipId(lineItems[REL_IDX_ID]);
		r.setCharacteristicType(SnomedUtils.translateCharacteristicType(lineItems[REL_IDX_CHARACTERISTICTYPEID]));
		r.setActive(lineItems[REL_IDX_ACTIVE].equals("1"));
		r.setModifier(SnomedUtils.translateModifier(lineItems[REL_IDX_MODIFIERID]));
		r.setModuleId(lineItems[REL_IDX_MODULEID]);
		//Set the effectiveTime last, as changing the other fields nulls it out
		r.setEffectiveTime(lineItems[REL_IDX_EFFECTIVETIME].isEmpty() ? null : lineItems[REL_IDX_EFFECTIVETIME]);
		return r;
	}

	private Concept lookupConcept(String sctId) throws TermServerScriptException {
		//Don't add concepts to the constructed snapshot that only the Terminology Server knows about
		Concept c = gl.getConcept(sctId, false, false);
		return c == null ? new Concept(sctId) : c;
	}

	private void reconcile(Component exported) throws TermServerScriptException {
		Component constructed = gl.getComponent(exported.getId());
		if (constructed == null) {
			reportNeedsDeleted(exported);
			return;
		}
		remainingComponents.remove(exported.getId());

		List<String> differences;
		try {
			differences = constructed.fieldComparison(exported);
		} catch (Exception e) {
			reportNotRestored(constructed, exported, "Unable to compare: " + e.getMessage());
			return;
		}

		if (differences.isEmpty()) {
			incrementSummaryCount(getTabName(constructed), MATCHED);
		} else if (StringUtils.isEmpty(exported.getEffectiveTime())) {
			reportNotRestored(constructed, exported, "Terminology Server holds an unpublished change - edited during this run?\n" + String.join("\n", differences));
		} else {
			restore(constructed, exported, RESTORED_DIFFERENT, String.join("\n", differences));
		}
	}

	private void restoreMissingComponents() throws TermServerScriptException {
		LOGGER.info("{} components in the constructed snapshot were not found in the Terminology Server export", remainingComponents.size());
		for (Component constructed : remainingComponents.values()) {
			restore(constructed, null, RESTORED_MISSING, "Not present in Terminology Server export");
		}
	}

	private void restore(Component constructed, Component exported, String action, String details) throws TermServerScriptException {
		Concept owner = gl.getComponentOwner(constructed.getId());
		if (StringUtils.isEmpty(constructed.getEffectiveTime())) {
			reportNotRestored(constructed, exported, "Unpublished in the constructed snapshot - edited during this run?\n" + details);
		} else if (owner == null) {
			reportNotRestored(constructed, exported, "Owning concept not known\n" + details);
		} else if (constructed instanceof RefsetMember rm && !canOutputRF2(rm)) {
			reportNotRestored(constructed, exported, "No delta file available for this refset pattern\n" + details);
		} else {
			constructed.setDirty();
			conceptsToOutput.add(owner);
			report(getTabIdx(constructed), owner, Severity.HIGH, action, constructed.getId(), details, constructed, exported == null ? "" : exported);
			incrementSummaryCount(getTabName(constructed), action);
		}
	}

	private void reportNotRestored(Component constructed, Component exported, String details) throws TermServerScriptException {
		Concept owner = gl.getComponentOwner(constructed.getId());
		reportComponent(getTabIdx(constructed), owner, Severity.CRITICAL, NOT_RESTORED, constructed.getId(), details, constructed, exported == null ? "" : exported);
		incrementSummaryCount(getTabName(constructed), NOT_RESTORED);
	}

	private void reportNeedsDeleted(Component exported) throws TermServerScriptException {
		//We'd expect anything unpublished to have come through in the delta, so this would be a surprise
		Severity severity = StringUtils.isEmpty(exported.getEffectiveTime()) ? Severity.CRITICAL : Severity.HIGH;
		String details = exported.getComponentType() + " is not in the constructed snapshot";
		reportComponent(NEEDS_DELETED_TAB, findOwner(exported), severity, NEEDS_DELETED, exported.getId(), details, "", exported);
		incrementSummaryCount(getTabName(exported), NEEDS_DELETED);
	}

	private void reportComponent(int tabIdx, Concept owner, Object... details) throws TermServerScriptException {
		if (owner == null) {
			List<Object> row = new ArrayList<>(List.of("", "", ""));
			row.addAll(Arrays.asList(details));
			report(tabIdx, row.toArray());
		} else {
			report(tabIdx, owner, details);
		}
	}

	private Concept findOwner(Component c) throws TermServerScriptException {
		String referencedId = null;
		if (c instanceof Concept) {
			referencedId = c.getId();
		} else if (c instanceof Description d) {
			referencedId = d.getConceptId();
		} else if (c instanceof Relationship r) {
			referencedId = r.getSourceId();
		} else if (c instanceof AlternateIdentifier altId) {
			referencedId = altId.getReferencedComponentId();
		} else if (c instanceof RefsetMember rm) {
			referencedId = rm.getReferencedComponentId();
		}

		if (referencedId == null) {
			return null;
		} else if (SnomedUtils.isConceptSctid(referencedId)) {
			return gl.getConcept(referencedId, false, false);
		}
		Description d = gl.getDescription(referencedId, false, false);
		return d == null ? null : gl.getConcept(d.getConceptId(), false, false);
	}

	private int getTabIdx(Component c) {
		return switch (c.getComponentType()) {
			case CONCEPT -> 2;
			case DESCRIPTION, TEXT_DEFINITION -> 3;
			case STATED_RELATIONSHIP, INFERRED_RELATIONSHIP, ADDITIONAL_RELATIONSHIP -> 4;
			case AXIOM -> 5;
			case LANGREFSET -> 6;
			case ATTRIBUTE_VALUE -> 7;
			case HISTORICAL_ASSOCIATION -> 8;
			case ALTERNATE_IDENTIFIER -> 9;
			case COMPONENT_ANNOTATION -> 10;
			default -> OTHER_REFSETS_TAB;
		};
	}

	private String getTabName(Component c) {
		return TAB_NAMES[getTabIdx(c)];
	}
}
