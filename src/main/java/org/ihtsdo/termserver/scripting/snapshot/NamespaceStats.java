package org.ihtsdo.termserver.scripting.snapshot;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.cis.NamespaceHelper;
import org.ihtsdo.termserver.scripting.domain.ExecutionOptions;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import java.nio.charset.StandardCharsets;

import java.util.*;

import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

public class NamespaceStats extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(NamespaceStats.class);

	private static final Set<String> INTERNATIONAL_MODULE_IDS = Set.of(
			"900000000000207008",  // Core module
			"900000000000012004"  // Model component
	);

	public static final Map<String, String> releaseMap = Map.ofEntries(
			Map.entry("AR", "SnomedCT_Argentina-EditionRelease_PRODUCTION_20251120T120000Z.zip"),
			Map.entry("AU", "SnomedCT_ManagedServiceAU_PRODUCTION_AU1000036_20260228T120000Z.zip"),
			Map.entry("AT", "SnomedCT_ManagedServiceAT_PRODUCTION_AT1000234_20251215T120000Z.zip"),
			Map.entry("BE", "SnomedCT_ManagedServiceBE_PRODUCTION_BE1000172_20260215T120000Z.zip"),
			Map.entry("CA", "SnomedCT_Canadian_EditionRelease_PRODUCTION_20251130T120000Z.zip"),
			/*Map.entry("CL", "SnomedCT_ManagedServiceCL_PRODUCTION_CL1000202_20250915T120000Z.zip"),*/
			Map.entry("CZ", "SnomedCT_CzechRepublicSimplexEdition_Production_20250127T120000Z.zip"),
			Map.entry("DK", "SnomedCT_ManagedServiceDK_PRODUCTION_DK1000005_20250930T120000Z.zip"),
			Map.entry("EE", "SnomedCT_ManagedServiceEE_PRODUCTION_EE1000181_20251130T120000Z.zip"),
			Map.entry("FI", "EXT_ONLY_SnomedCT_Finland_EditionRelease_PRODUCTION_20251115T120000Z.zip"),
			Map.entry("FR", "SnomedCT_ManagedServiceFR_PRODUCTION_FR1000315_20250621T120000Z.zip"),
			Map.entry("DE", "SnomedCT_Germany-ExtensionRelease_PRODUCTION_20251115T120000Z.zip"),
			Map.entry("IE", "SnomedCT_ManagedServiceIE_PRODUCTION_IE1000220_20260221T120000Z.zip"),
			Map.entry("JM", "SnomedCT_JamaicanNationalSimplexEdition_Production_20250328T120000Z.zip"),
			Map.entry("KR", "SnomedCT_ManagedServiceKR_PRODUCTION_KR1000267_20251215T120000Z.zip"),
			Map.entry("NL", "EXT_ONLY_SnomedCT_ManagedServiceNL_PRODUCTION_NL1000146_20260228T120000Z.zip"),
			Map.entry("NZ", "SnomedCT_ManagedServiceNZ_PRODUCTION_NZ1000210_20251001T000000Z.zip"),
			Map.entry("NO", "SnomedCT_ManagedServiceNO_PRODUCTION_NO1000202_20260215T120000Z.zip"),
			Map.entry("SE", "SnomedCT_ManagedServiceSE_PRODUCTION_SE1000052_20251130T120000Z.zip"),
			Map.entry("CH", "SnomedCT_ManagedServiceCH_PRODUCTION_CH1000195_20251207T120000Z.zip"),
			Map.entry("US", "SnomedCT_ManagedServiceUS_PRODUCTION_US1000124_20260301T120000Z.zip"),
			Map.entry("UK", "uk_sct2mo_41.5.0_20260211000001Z.zip"),
			Map.entry("UY", "SnomedCT_Uruguay-Edition_PRODUCTION_20251215T120000Z.zip")
	);

	Map<String, List<String>> countryNamespaces = new HashMap<>() {{
		put("FI", Arrays.asList("1000288", "1000229"));
		put("UY", Arrays.asList("1000179", "1000321"));
		put("JM", Arrays.asList("1000318"));
		put("CH", Arrays.asList("1000195"));
		put("US", Arrays.asList("1000124", "1000119", "1000175", "1000004", "1000161", "1000224", "1000284", "1000046"));
		put("NO", Arrays.asList("1000202"));
		put("NL", Arrays.asList("1000146"));
		put("KR", Arrays.asList("1000267"));
		put("AT", Arrays.asList("1000234"));
		put("CA", Arrays.asList("1000087", "1000112"));
		put("AR", Arrays.asList("1000221"));
		put("DK", Arrays.asList("1000005"));
		put("UK", Arrays.asList("1000001", "1000000", "1000237"));
		put("DE", Arrays.asList("1000274"));
		put("CZ", Arrays.asList("1000279"));
		put("FR", Arrays.asList("1000315"));
		put("EE", Arrays.asList("1000181"));
		put("BE", Arrays.asList("1000172"));
		put("SE", Arrays.asList("1000052", "1000057"));
		put("NZ", Arrays.asList("1000210"));
		put("IE", Arrays.asList("1000220"));
	}};

	private NamespaceHelper namespaceHelper;

	Map<String, LateralPromotion> lateralPromotions = new HashMap<>();

	public static void main(String[] args) throws TermServerScriptException {
		new NamespaceStats().standardExecution(args, ExecutionOptions.DEFAULT);
	}

	@Override
	public void init(String[] args) throws TermServerScriptException {
		super.init(args);
		//Set the secondary server URL using the -s or --server parameters
		namespaceHelper = NamespaceHelper.create(getSecondaryServerUrl(), authenticatedCookie);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId(GFOLDER_TECHNICAL_SPECIALIST);
		String[] tabNames = new String[] {"Summary Counts", "Lateral Promotions"};
		String[] columnHeadings = new String[] {
				"Country,NameSpace,Owner,Count,Belongs?",
				"Exists in Country, Namespace, SCTID, Assumed Source Country"};
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	public void runJob() throws TermServerScriptException {
		List<Map.Entry<String, String>> sortedEntries = new ArrayList<>(releaseMap.entrySet());
		sortedEntries.sort(Map.Entry.comparingByKey());
		for (Map.Entry<String, String> entry : sortedEntries) {
			analyzeExtension(entry.getKey(), "releases/" + entry.getValue());
		}
	}

	private String findCountryOwnerOfNameSpace(String namespace) {
		for (Map.Entry<String, List<String>> e : countryNamespaces.entrySet()) {
			if (e.getValue().contains(namespace)) {
				return e.getKey();
			}
		}
		return null; // or Optional<String>
	}

	public void analyzeExtension(String shortName, String releaseFileName) throws TermServerScriptException {
		LOGGER.info("Analyzing extension {} from release file {}", shortName, releaseFileName);

		File zipFile = new File(releaseFileName);
		if (!zipFile.exists()) {
			LOGGER.warn("Release file not found: {}", releaseFileName);
			return;
		}

		Map<String, Long> namespaceCounts = extractNamespaceCounts(shortName, zipFile);
		outputNamespaceSummary(shortName, namespaceCounts);
		outputLateralPromotions();
	}

	private void outputLateralPromotions() throws TermServerScriptException {
		// Output header if required
		for (LateralPromotion lp : lateralPromotions.values()) {
			report(SECONDARY_REPORT,
					lp.existsInCountry,
					lp.originatingNamespace + " | " + namespaceHelper.getOwner(lp.originatingNamespace),
					lp.sctId + " | " + lp.fsn,
					lp.assumedSourceCountry);
		}
		//clean up the map for the next country
		lateralPromotions.clear();
	}

	// --- Helper: extract namespace counts from ZIP ---
	private Map<String, Long> extractNamespaceCounts(String shortName, File zipFile) throws TermServerScriptException {
		Map<String, Long> namespaceCounts = new HashMap<>();

		try (ZipFile zip = new ZipFile(zipFile)) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (isRequiredFile("concept", entry)) {
					LOGGER.info("Processing concept snapshot file: {}", entry.getName());
					processConceptSnapshot(shortName, zip, entry, namespaceCounts);
				} else if (isRequiredFile("description", entry)) {
					LOGGER.info("Processing description snapshot file: {}", entry.getName());
					processDescriptionSnapshot(shortName, zip, entry, namespaceCounts);
				}
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Failed processing " + zipFile.getName(), e);
		}

		return namespaceCounts;
	}

	// --- Helper: check if a ZIP entry is a concept snapshot file ---
	private boolean isRequiredFile(String filter, ZipEntry entry) {
		String name = entry.getName().toLowerCase();
		return name.contains(filter) && name.contains("snapshot") && name.endsWith(".txt");
	}

	// --- Helper: process a single concept snapshot and update namespace counts ---
	private void processConceptSnapshot(String shortName, ZipFile zip, ZipEntry entry, Map<String, Long> namespaceCounts) throws TermServerScriptException, IOException {
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {

			String line;
			boolean firstLine = true;
			while ((line = br.readLine()) != null) {
				if (firstLine) { firstLine = false; continue; } // skip header

				String[] columns = line.split("\t", -1);
				String conceptId = columns[0];
				if (isInternationalConcept(conceptId, columns[3])) continue;

				String namespace = SnomedUtilsBase.getNamespace(conceptId);
				namespaceCounts.merge(namespace, 1L, Long::sum);
				checkForLateralPromotion(shortName, conceptId, namespace, "");
			}
		}
	}

	private void processDescriptionSnapshot(String shortName, ZipFile zip, ZipEntry entry, Map<String, Long> namespaceCounts) throws TermServerScriptException, IOException {
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {

			String line;
			boolean firstLine = true;
			while ((line = br.readLine()) != null) {
				if (firstLine) { firstLine = false; continue; } // skip header

				String[] columns = line.split("\t", -1);
				if (!columns[2].equals("1") || !columns[6].equals("900000000000003001")) {
					continue; // Only active, fully specified names
				}
				String conceptId = columns[4];
				if (isInternationalConcept(conceptId, columns[3])) continue;

				String namespace = SnomedUtilsBase.getNamespace(conceptId);
				namespaceCounts.merge(namespace, 1L, Long::sum);
				checkForLateralPromotion(shortName, conceptId, namespace, columns[7]);
			}
		}
	}

	private void checkForLateralPromotion(String shortName, String conceptId, String namespace, String term) {
		// Is this namespace one that we would normally associate with this country?
		if (!countryNamespaces.getOrDefault(shortName, Collections.emptyList()).contains(namespace)) {
			LateralPromotion existing = lateralPromotions.get(conceptId);
			if (existing == null) {
				// First time seeing this concept
				lateralPromotions.put(conceptId, createLateralPromotion(conceptId, namespace, shortName, term));
			} else if (!StringUtils.isEmpty(term)) {
				// Already exists, but new term is non-empty → replace fsn
				existing.fsn = term;
			}
		}
	}

	// --- Helper to create a new LateralPromotion object ---
	private LateralPromotion createLateralPromotion(String conceptId, String namespace, String shortName, String term) {
		LateralPromotion lp = new LateralPromotion();
		lp.sctId = conceptId;
		lp.originatingNamespace = namespace;
		lp.existsInCountry = shortName;
		lp.assumedSourceCountry = findCountryOwnerOfNameSpace(namespace);
		lp.fsn = term;
		return lp;
	}

	// --- Helper: determine if a concept should be skipped ---
	private boolean isInternationalConcept(String conceptId, String moduleId) throws TermServerScriptException {
		String partition = conceptId.substring(conceptId.length() - 3, conceptId.length() - 1);
		return "00".equals(partition)
				|| INTERNATIONAL_MODULE_IDS.contains(moduleId)
				|| gl.getConcept(conceptId, false, false) != null;
	}

	// --- Helper: output namespace summary ---
	private void outputNamespaceSummary(String shortName, Map<String, Long> namespaceCounts) throws TermServerScriptException {
		LOGGER.info("Outputting namespace summary for {}.", shortName);
		report(PRIMARY_REPORT, shortName);

		List<Map.Entry<String, Long>> sortedEntries = new ArrayList<>(namespaceCounts.entrySet());
		sortedEntries.sort(Map.Entry.<String, Long>comparingByValue().reversed());

		for (Map.Entry<String, Long> entry : sortedEntries) {
			String namespaceOwner = namespaceHelper.getOwner(entry.getKey());
			//Does this namespace belong to this country?
			String belongs = countryNamespaces.getOrDefault(shortName, Collections.emptyList()).contains(entry.getKey()) ? "Y" : "N";
			report(PRIMARY_REPORT, "", entry.getKey(), namespaceOwner, entry.getValue(), belongs);
		}
	}

	class LateralPromotion {
		String sctId;
		String originatingNamespace;
		String existsInCountry;
		String assumedSourceCountry;
		String fsn;
	}


}
