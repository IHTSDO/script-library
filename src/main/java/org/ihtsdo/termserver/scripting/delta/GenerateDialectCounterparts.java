package org.ihtsdo.termserver.scripting.delta;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.DialectChecker;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.UserInteractionHelper;
import org.ihtsdo.termserver.scripting.util.WhitelistHelper;
import org.snomed.otf.scheduler.domain.WhiteListedConcept;

/**
 * RP-1173 Create the missing other-dialect counterpart for descriptions featuring US or GB specific spelling.
 * Concepts whitelisted for the Release Issues Report are skipped, as their dialect specific terms are often
 * deliberate, eg the proper name of an organization.
 */
public class GenerateDialectCounterparts extends DeltaGenerator implements ScriptConstants {

	private static final String RELEASE_ISSUES_REPORT = "Release Issues Report";
	private static final int SUMMARY_TAB = PRIMARY_REPORT;
	private static final int DETAILS_TAB = SECONDARY_REPORT;
	private static final int CLINICAL_DRUGS_TAB = TERTIARY_REPORT;
	private static final int UNITS_TAB = QUATERNARY_REPORT;
	private static final int BLOOD_TAB = QUINARY_REPORT;
	private static final int WHITELIST_TAB = SENARY_REPORT;
	private static final int SKIPPED_TAB = SEPTENARY_REPORT;
	private static final String DETAILS_HEADINGS = "SCTID, FSN, SemTag, Severity, Action, Word(s), Description, Details, Likely Counterpart, ";
	private static final String POSSIBLE_MISSING_MAP_PAIR = "Possible missing map pair";

	private static final String WHITELIST_CATEGORY = "Whitelist";
	private static final String CONCEPTS_CATEGORY = "Concepts";
	private static final String CREATED_CATEGORY = "Counterparts created";
	private static final String NOT_CREATED_CATEGORY = "Counterparts not created";
	private static final String US = "US";
	private static final String GB = "GB";

	//A concept whose FSN features any of these words or phrases (case sensitive) is skipped, eg as a proper name
	private static final List<String> SKIP_WORDS = List.of("International", "TNM", "World Health Organization", "Conners", "Canadian");
	//Concepts with these semtags and an entire term case sensitive description are proper names, so are skipped
	private static final List<String> CS_SKIP_SEMTAGS = List.of("(assessment scale)");
	//One SCTID per line, anything after the SCTID (eg the FSN) is ignored, as are blank lines and # comments
	private static final String SKIPPED_CONCEPTS_FILENAME = "skipped_concepts.txt";
	private static final String MANUALLY_SPECIFIED = "Manually Specified";
	//Words are runs of letters, as for the DialectChecker
	private static final Pattern WORD = Pattern.compile("\\p{L}+");
	private static final Map<String, Pattern> SKIP_PATTERNS = SKIP_WORDS.stream()
			.collect(Collectors.toMap(w -> w, w -> Pattern.compile("\\b" + Pattern.quote(w) + "\\b")));
	//Concepts with any dialect word matching these get the tab, the first match winning
	private static final Map<Integer, Pattern> DIALECT_WORD_TABS = new LinkedHashMap<>();
	static {
		DIALECT_WORD_TABS.put(UNITS_TAB, Pattern.compile("^(micro|milli|centi|deci|kilo)?(liter|litre|meter|metre)s?$", Pattern.CASE_INSENSITIVE));
		DIALECT_WORD_TABS.put(BLOOD_TAB, Pattern.compile("haem|^hem|aemi|emia|emic|leucocyt|leukocyt|thalass", Pattern.CASE_INSENSITIVE));
	}

	private final UserInteractionHelper ui = new UserInteractionHelper(this);
	private DialectChecker dialectChecker;
	private Set<String> manuallySkippedConceptIds = new HashSet<>();
	private String whiteListCodeSystem;
	private final Map<String, Integer> detailsTabByConceptId = new HashMap<>();

	public static void main(String[] args) throws TermServerScriptException {
		new GenerateDialectCounterparts().standardExecution(args);
	}

	@Override
	protected void init(String[] args) throws TermServerScriptException {
		super.init(args);
		//Ask now, rather than after the snapshot has loaded
		whiteListCodeSystem = ui.askWithDefault("Code system for the " + RELEASE_ISSUES_REPORT + " whitelist", WhitelistHelper.DEFAULT_CODE_SYSTEM);
	}

	@Override
	public void postInit(String googleFolder) throws TermServerScriptException {
		summaryTabIdx = SUMMARY_TAB;
		//Spare columns, as summary text lines are split on every colon
		String[] columnHeadings = new String[]{
				"Category, Item, Count, , , ",
				DETAILS_HEADINGS,
				DETAILS_HEADINGS,
				DETAILS_HEADINGS,
				DETAILS_HEADINGS,
				"SCTID, FSN",
				"SCTID, FSN, SemTag, Reason, Dialect Word(s)"
		};

		String[] tabNames = new String[]{
				"Summary",
				"Details",
				"Clinical Drugs",
				"Units",
				"Blood",
				"Whitelist",
				"Skipped"
		};
		super.postInit(googleFolder, tabNames, columnHeadings);
	}

	@Override
	protected void process() throws TermServerScriptException {
		dialectChecker = DialectChecker.createIncludingSynonyms();
		recoverWhiteList();
		loadManuallySkippedConcepts();

		for (Concept c : SnomedUtils.sortActive(gl.getAllConcepts())) {
			if (whitelistHelper.isWhiteListed(c)) {
				incrementSummaryCount(CONCEPTS_CATEGORY, "Whitelisted, skipped");
			} else if (getSkipReason(c) != null) {
				reportSkipped(c, getSkipReason(c));
			} else if (processConcept(c) && outputRF2(c)) {
				incrementSummaryCount(CONCEPTS_CATEGORY, "Modified");
				recordConceptWritten();
			}
		}
		reportSummaryCounts(SUMMARY_TAB);
	}

	private void recoverWhiteList() throws TermServerScriptException {
		whitelistHelper.populateViaAPI(url, getAuthenticatedCookie(), RELEASE_ISSUES_REPORT, whiteListCodeSystem);

		String whiteListItem = RELEASE_ISSUES_REPORT + " concepts whitelisted in " + whiteListCodeSystem;
		initialiseSummaryCount(WHITELIST_CATEGORY, whiteListItem);
		for (WhiteListedConcept wlc : whitelistHelper.getWhiteListSorted()) {
			report(WHITELIST_TAB, wlc.getSctId(), wlc.getFsn());
			incrementSummaryCount(WHITELIST_CATEGORY, whiteListItem);
		}
	}

	private void loadManuallySkippedConcepts() throws TermServerScriptException {
		if (StringUtils.isEmpty(baseStorageDirectory)) {
			throw new TermServerScriptException("Specify " + BASE_STORAGE_DIRECTORY_ARG + " <directory> to locate " + SKIPPED_CONCEPTS_FILENAME);
		}
		Path skippedConceptsFile = Paths.get(baseStorageDirectory, SKIPPED_CONCEPTS_FILENAME);
		try {
			manuallySkippedConceptIds = Files.readAllLines(skippedConceptsFile).stream()
					.map(String::trim)
					.filter(line -> !line.isEmpty() && !line.startsWith("#"))
					.map(line -> line.split("\\s")[0])
					.collect(Collectors.toSet());
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to read " + skippedConceptsFile, e);
		}
		LOGGER.info("Loaded {} manually skipped concepts from {}", manuallySkippedConceptIds.size(), skippedConceptsFile);
	}

	/**
	 * @return why the concept is to be skipped, or null if it isn't
	 */
	private String getSkipReason(Concept c) {
		if (manuallySkippedConceptIds.contains(c.getId())) {
			return MANUALLY_SPECIFIED;
		}
		if (isCaseSensitiveProperName(c)) {
			return "Case sensitive " + c.getSemTag();
		}
		String fsn = c.getFsn();
		return SKIP_WORDS.stream()
				.filter(w -> SKIP_PATTERNS.get(w).matcher(fsn).find())
				.findFirst()
				.map(w -> "Term Matched \"" + w + "\"")
				.orElse(null);
	}

	private boolean isCaseSensitiveProperName(Concept c) {
		return CS_SKIP_SEMTAGS.contains(c.getSemTag()) && c.getDescriptions(ActiveState.ACTIVE).stream()
				.anyMatch(d -> d.getCaseSignificance() == CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
	}

	/**
	 * Concepts matched on a skip word are only of interest if they feature some dialect specific word,
	 * but manually specified concepts are always listed.
	 */
	private void reportSkipped(Concept c, String reason) throws TermServerScriptException {
		Set<String> dialectWords = getDialectWords(c);
		if (!dialectWords.isEmpty() || reason.equals(MANUALLY_SPECIFIED)) {
			report(SKIPPED_TAB, c, reason, String.join(", ", dialectWords));
			incrementSummaryCount(CONCEPTS_CATEGORY, "Skipped, " + reason);
		}
	}

	private boolean processConcept(Concept c) throws TermServerScriptException {
		boolean changesMade = false;
		//Take a copy, as we'll be adding descriptions as we go
		List<Description> synonyms = c.getDescriptions(ActiveState.ACTIVE).stream()
				.filter(d -> d.getType().equals(DescriptionType.SYNONYM))
				.filter(this::inScope)
				.toList();
		for (Description d : synonyms) {
			changesMade |= processSynonym(c, d);
		}
		//Done after the synonyms, which will usually have supplied the FSN's counterpart already
		changesMade |= ensureFsnCounterpart(c);
		return changesMade;
	}

	private boolean processSynonym(Concept c, Description d) throws TermServerScriptException {
		String term = d.getTerm();
		String usTerm = dialectChecker.makeUSSpecific(term);
		String gbTerm = dialectChecker.makeGBSpecific(term);
		boolean hasGbWord = !usTerm.equals(term);
		boolean hasUsWord = !gbTerm.equals(term);
		String words = getDialectWords(term, usTerm, gbTerm);

		if (hasUsWord && hasGbWord) {
			return splitMixedDialectSynonym(c, d, words, usTerm, gbTerm);
		} else if (hasUsWord) {
			return addCounterpart(c, d, words, gbTerm, US_ENG_LANG_REFSET, GB_ENG_LANG_REFSET);
		} else if (hasGbWord && matchesFsnSpelling(c, words)) {
			report(getDetailsTab(c), c, Severity.MEDIUM, ReportActionType.NO_CHANGE, words, d.toStringWithoutIds(), "Matches the GB spelling in the FSN, taken to be the correct name");
			incrementSummaryCount(NOT_CREATED_CATEGORY, "Synonym matches FSN's GB spelling");
			return false;
		} else if (hasGbWord) {
			return addCounterpart(c, d, words, usTerm, GB_ENG_LANG_REFSET, US_ENG_LANG_REFSET);
		}
		return false;
	}

	/**
	 * The FSN's spelling is taken to be the correct name, so a synonym whose dialect specific words all appear in
	 * the FSN may be acceptable in both dialects.  GB spelling in the FSN is taken to be deliberate (eg the name of
	 * an organisation), so a synonym using it gets no US counterpart either.
	 * @param dialectWords the synonym's dialect specific words, as listed by getDialectWords
	 */
	private boolean matchesFsnSpelling(Concept c, String dialectWords) {
		Description fsn = c.getFSNDescription();
		if (fsn == null || dialectWords.isEmpty()) {
			return false;
		}
		Set<String> fsnWords = new HashSet<>(getWords(fsn.getTerm().toLowerCase()));
		return fsnWords.containsAll(Arrays.asList(dialectWords.toLowerCase().split(", ")));
	}

	/**
	 * The original becomes specific to its own dialect, and the counterpart takes the acceptability the original
	 * had in the other dialect.  If the original was already specific to its own dialect, the counterpart takes
	 * the same acceptability, but only as acceptable if the other dialect already has a preferred term.
	 */
	private boolean addCounterpart(Concept c, Description d, String words, String counterpartTerm, String thisRefsetId, String otherRefsetId) throws TermServerScriptException {
		Description existingCounterpart = findActiveDescription(c, counterpartTerm);
		if (existingCounterpart != null) {
			if (d.getAcceptability(otherRefsetId) != null && !matchesFsnSpelling(c, words)) {
				report(getDetailsTab(c), c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, words, d.toStringWithoutIds(), "Counterpart '" + existingCounterpart.getTerm() + "' already present, but original is still acceptable in both dialects");
				incrementSummaryCount(NOT_CREATED_CATEGORY, "Counterpart already present, original in both dialects");
			} else if (d.getAcceptability(otherRefsetId) != null) {
				incrementSummaryCount(NOT_CREATED_CATEGORY, "Counterpart already present, original in both dialects, matches FSN");
			} else {
				incrementSummaryCount(NOT_CREATED_CATEGORY, "Counterpart already present");
			}
			return false;
		}

		Description likelyCounterpart = findLikelyCounterpart(c, counterpartTerm, thisRefsetId, otherRefsetId);
		if (likelyCounterpart != null) {
			String missingPairs = getMissingMapPairs(d.getTerm(), counterpartTerm, likelyCounterpart.getTerm(), thisRefsetId);
			report(getDetailsTab(c), c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, words, d.toStringWithoutIds(), POSSIBLE_MISSING_MAP_PAIR + " " + missingPairs, likelyCounterpart.toStringWithoutIds());
			incrementSummaryCount(NOT_CREATED_CATEGORY, POSSIBLE_MISSING_MAP_PAIR);
			return false;
		}

		Acceptability counterpartAcceptability = d.getAcceptability(otherRefsetId);
		if (counterpartAcceptability != null) {
			d.removeAcceptability(otherRefsetId, false);
		} else {
			counterpartAcceptability = d.getAcceptability(thisRefsetId);
			if (counterpartAcceptability == Acceptability.PREFERRED && c.getPreferredSynonym(otherRefsetId) != null) {
				counterpartAcceptability = Acceptability.ACCEPTABLE;
			}
		}

		if (counterpartAcceptability == null) {
			incrementSummaryCount(NOT_CREATED_CATEGORY, "Original has no acceptability");
			return false;
		}

		Description counterpart = createDialectDescription(c, d, counterpartTerm, otherRefsetId, counterpartAcceptability);
		report(getDetailsTab(c), c, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, words, counterpart.toStringWithoutIds(), "Counterpart of " + d.toStringWithoutIds());
		incrementSummaryCount(CREATED_CATEGORY, describe(counterpartAcceptability, otherRefsetId));
		return true;
	}

	/**
	 * An existing description, specific to the other dialect, that agrees with the counterpart on every converted
	 * word but differs on others is most likely the counterpart already, and the map lacks a pair, eg gray/grey
	 */
	private Description findLikelyCounterpart(Concept c, String counterpartTerm, String thisRefsetId, String otherRefsetId) throws TermServerScriptException {
		List<String> counterpartWords = getWords(counterpartTerm);
		for (Description d : c.getDescriptions(ActiveState.ACTIVE, List.of(DescriptionType.SYNONYM))) {
			if (d.getAcceptability(otherRefsetId) != null && d.getAcceptability(thisRefsetId) == null
					&& differsOnlyOnUnconvertedWords(counterpartWords, getWords(d.getTerm()))) {
				return d;
			}
		}
		return null;
	}

	/**
	 * The two terms may differ (other than in case) only on words the map doesn't know, ie possible missing pairs
	 */
	private boolean differsOnlyOnUnconvertedWords(List<String> counterpartWords, List<String> existingWords) {
		if (counterpartWords.size() != existingWords.size()) {
			return false;
		}
		for (int i = 0; i < counterpartWords.size(); i++) {
			String counterpartWord = counterpartWords.get(i);
			if (!counterpartWord.equalsIgnoreCase(existingWords.get(i)) && dialectChecker.containsUSGBSpecificTerm(counterpartWord)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @return the words the map is likely missing, as US/GB pairs
	 */
	private String getMissingMapPairs(String originalTerm, String counterpartTerm, String existingTerm, String thisRefsetId) {
		List<String> originalWords = getWords(originalTerm);
		List<String> counterpartWords = getWords(counterpartTerm);
		List<String> existingWords = getWords(existingTerm);
		Set<String> pairs = new LinkedHashSet<>();
		for (int i = 0; i < originalWords.size(); i++) {
			if (!counterpartWords.get(i).equalsIgnoreCase(existingWords.get(i))) {
				String original = originalWords.get(i).toLowerCase();
				String existing = existingWords.get(i).toLowerCase();
				pairs.add(thisRefsetId.equals(US_ENG_LANG_REFSET) ? original + "/" + existing : existing + "/" + original);
			}
		}
		return String.join(", ", pairs);
	}

	/**
	 * A term featuring both US and GB specific words is inactivated, and replaced with one term using only US
	 * spelling, and another using only GB spelling, each taking the original's acceptability in that dialect.
	 */
	private boolean splitMixedDialectSynonym(Concept c, Description d, String words, String usTerm, String gbTerm) throws TermServerScriptException {
		report(getDetailsTab(c), c, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, words, d.toStringWithoutIds(), "Term mixes US and GB specific spelling.  Inactivated and replaced with a US and a GB term.");
		Acceptability usAcceptability = d.getAcceptability(US_ENG_LANG_REFSET);
		Acceptability gbAcceptability = d.getAcceptability(GB_ENG_LANG_REFSET);

		//Clone before inactivating, as inactivation also inactivates the original's lang refset entries
		addReplacementIfRequired(c, d, words, usTerm, US_ENG_LANG_REFSET, usAcceptability);
		addReplacementIfRequired(c, d, words, gbTerm, GB_ENG_LANG_REFSET, gbAcceptability);

		d.setActive(false);
		d.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
		report(getDetailsTab(c), c, Severity.LOW, ReportActionType.DESCRIPTION_INACTIVATED, words, d.toStringWithoutIds());
		incrementSummaryCount(CONCEPTS_CATEGORY, "Mixed dialect terms split");
		return true;
	}

	private void addReplacementIfRequired(Concept c, Description mixed, String words, String term, String refsetId, Acceptability acceptability) throws TermServerScriptException {
		if (acceptability == null || hasActiveDescription(c, term)) {
			return;
		}
		Description replacement = createDialectDescription(c, mixed, term, refsetId, acceptability);
		report(getDetailsTab(c), c, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, words, replacement.toStringWithoutIds(), "Replaces mixed dialect " + mixed.toStringWithoutIds());
		incrementSummaryCount(CREATED_CATEGORY, describe(acceptability, refsetId));
	}

	/**
	 * We don't create a GB FSN.  A GB specific word in the FSN is taken to be deliberate, but a US specific word
	 * means we check that the GB form of the FSN (less its semantic tag) is available as a GB synonym.
	 */
	private boolean ensureFsnCounterpart(Concept c) throws TermServerScriptException {
		Description fsn = c.getFSNDescription();
		if (fsn == null || !inScope(fsn)) {
			return false;
		}
		String fsnBase = SnomedUtilsBase.deconstructFSN(fsn.getTerm())[0];
		String gbBase = dialectChecker.makeGBSpecific(fsnBase);
		boolean hasUsWord = !gbBase.equals(fsnBase);
		String usBase = dialectChecker.makeUSSpecific(fsnBase);
		boolean hasGbWord = !usBase.equals(fsnBase);
		String words = getDialectWords(fsnBase, usBase, gbBase);

		if (hasGbWord) {
			report(getDetailsTab(c), c, Severity.MEDIUM, ReportActionType.NO_CHANGE, words, fsn.toStringWithoutIds(), "GB specific spelling in FSN taken to be deliberate");
			incrementSummaryCount(NOT_CREATED_CATEGORY, "FSN with GB spelling");
			return false;
		}
		if (!hasUsWord || hasActiveDescription(c, gbBase)) {
			return false;
		}

		Description gbSynonym = Description.withDefaults(gbBase, DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
		gbSynonym.setCaseSignificance(fsn.getCaseSignificance());
		gbSynonym.removeAcceptability(US_ENG_LANG_REFSET, false);
		addToConcept(c, gbSynonym);
		report(getDetailsTab(c), c, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, words, gbSynonym.toStringWithoutIds(), "GB form of FSN");
		incrementSummaryCount(CREATED_CATEGORY, describe(Acceptability.ACCEPTABLE, GB_ENG_LANG_REFSET) + " from FSN");
		return true;
	}

	private Description createDialectDescription(Concept c, Description template, String term, String refsetId, Acceptability acceptability) throws TermServerScriptException {
		Description newDesc = template.clone(null);
		newDesc.setTerm(term);
		//Specific to the one dialect
		for (String langRefsetId : List.of(US_ENG_LANG_REFSET, GB_ENG_LANG_REFSET)) {
			if (!langRefsetId.equals(refsetId)) {
				newDesc.removeAcceptability(langRefsetId, false);
			}
		}
		newDesc.setAcceptability(refsetId, acceptability);
		addToConcept(c, newDesc);
		return newDesc;
	}

	private void addToConcept(Concept c, Description d) throws TermServerScriptException {
		if (descIdGenerator == null) {
			throw new TermServerScriptException("No description SCTIDs available: supply -iD <file>, or -iD DUMMY for a trial run");
		}
		d.setId(descIdGenerator.getSCTID());
		d.setConceptId(c.getId());
		d.setModuleId(targetModuleId);
		for (LangRefsetEntry l : d.getLangRefsetEntries()) {
			l.setReferencedComponentId(d.getId());
			l.setModuleId(targetModuleId);
		}
		c.addDescription(d);
	}

	private boolean hasActiveDescription(Concept c, String term) {
		return findActiveDescription(c, term) != null;
	}

	/**
	 * Matched on words, ignoring case, so a description differing only in punctuation or spacing still counts,
	 * eg "peacock-pheasant" for "peacock pheasant"
	 */
	private Description findActiveDescription(Concept c, String term) {
		List<String> words = getWords(term.toLowerCase());
		return c.getDescriptions(ActiveState.ACTIVE).stream()
				.filter(d -> getWords(d.getTerm().toLowerCase()).equals(words))
				.findFirst()
				.orElse(null);
	}

	/**
	 * The words in the original term that the dialect conversions changed.  Conversion swaps word for word, so
	 * the words line up by position.
	 */
	private String getDialectWords(String term, String... conversions) {
		List<String> originalWords = getWords(term);
		Set<String> dialectWords = new LinkedHashSet<>();
		for (String conversion : conversions) {
			List<String> convertedWords = getWords(conversion);
			for (int i = 0; i < originalWords.size() && i < convertedWords.size(); i++) {
				if (!originalWords.get(i).equals(convertedWords.get(i))) {
					dialectWords.add(originalWords.get(i));
				}
			}
		}
		return String.join(", ", dialectWords);
	}

	/**
	 * The dialect words across all the concept's active descriptions
	 */
	private Set<String> getDialectWords(Concept c) {
		Set<String> dialectWords = new LinkedHashSet<>();
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			String term = d.getTerm();
			String words = getDialectWords(term, dialectChecker.makeUSSpecific(term), dialectChecker.makeGBSpecific(term));
			if (!words.isEmpty()) {
				dialectWords.addAll(Arrays.asList(words.split(", ")));
			}
		}
		return dialectWords;
	}

	/**
	 * The term's words, without any punctuation, eg brackets, commas or slashes
	 */
	private List<String> getWords(String term) {
		List<String> words = new ArrayList<>();
		Matcher matcher = WORD.matcher(term);
		while (matcher.find()) {
			words.add(matcher.group());
		}
		return words;
	}

	/**
	 * Clinical drugs pick up counterparts in volume (eg milliliter), so they get a tab of their own, as do
	 * units and blood words.  Routed by concept, on first use, so all the concept's rows land on the same tab.
	 */
	private int getDetailsTab(Concept c) {
		return detailsTabByConceptId.computeIfAbsent(c.getId(), id -> chooseDetailsTab(c));
	}

	private int chooseDetailsTab(Concept c) {
		if (DrugUtils.CD.equals(c.getSemTag())) {
			return CLINICAL_DRUGS_TAB;
		}
		Set<String> dialectWords = getDialectWords(c);
		return DIALECT_WORD_TABS.entrySet().stream()
				.filter(e -> dialectWords.stream().anyMatch(w -> e.getValue().matcher(w).find()))
				.map(Map.Entry::getKey)
				.findFirst()
				.orElse(DETAILS_TAB);
	}

	private String describe(Acceptability acceptability, String refsetId) {
		String dialect = refsetId.equals(US_ENG_LANG_REFSET) ? US : GB;
		return dialect + " " + (acceptability == Acceptability.PREFERRED ? "PT" : "acceptable synonym");
	}
}
