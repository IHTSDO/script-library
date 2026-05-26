package org.ihtsdo.termserver.scripting.fhir;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r5.model.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.ExecutionOptions;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Specify the sever URL for testing with -s or --server arg
 */
public class FhirR5APITester extends FhirAPITesterBase {

	private static final FhirContext FHIR_CONTEXT = FhirContext.forR5();

	static final String ICD11_MMS_SYSTEM      = "http://id.who.int/icd/release/11/mms";
	static final String ICD11_ICF_SYSTEM      = "http://id.who.int/icd/release/11/icf";
	static final String ICD11_FOUNDATION_SYSTEM = "http://id.who.int/icd/entity";

	static final String ICD11_VERSION      = "2025-01";
	static final String ICD11_MMS_CS_ID    = "ICD-11-MMS-2025-01";

	// Well-known ICD-11 MMS codes used as test fixtures
	private static final String CODE_COVID19 = "RA01";
	private static final String CODE_INVALID = "ZZZZZ";
	private static final String CODE_CHOLERA = "1A03";

	// Foundation URI for Cholera entity
	private static final String CODE_FOUNDATION_CHOLERA = "http://id.who.int/icd/entity/416025325";

	// Postcoordinated codes (& is the ICD-11 postcoordination separator)
	private static final String CODE_POSTCRD_MMS          = "DB91.0&XN0GA";
	private static final String CODE_POSTCRD_VALID        = "1A00&XN8P1";
	private static final String CODE_POSTCRD_INVALID      = "1A00&XN44G";
	private static final String CODE_POSTCRD_MULTI_AXIS   = "EH90.0&XK8G&XA22Q1/MB44.3/PK80.41";

	// ICF code: Mobility of joint functions
	private static final String CODE_ICF_B710 = "b710";

	// Postcoordination scale ValueSet URLs and member code
	private static final String VS_URL_POSTCRD_ASSOC_WITH  =
			"http://id.who.int/icd/release/11/mms/257068234/postcoordinationScale/associatedWith";
	private static final String VS_URL_POSTCRD_INFECTIOUS  =
			"http://id.who.int/icd/release/11/mms/257068234/postcoordinationScale/infectiousAgent";
	private static final String CODE_INFECTIOUS_AGENT = "XN62R";

	@Override
	protected FhirContext getFhirContext() {
		return FHIR_CONTEXT;
	}

	public static void main(String[] args) throws TermServerScriptException {
		new FhirR5APITester().standardExecution(args, new ExecutionOptions().withNoSnapshotImport());
	}

	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("FHIR R5 API Tester — ICD-11 WHO Connectathon")
				.withDescription("Exercises FHIR R5 terminology operations against the WHO ICD-11 API")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(new JobParameters())
				.withTag(INT)
				.build();
	}

	@Override
	protected void runTests() throws TermServerScriptException {
		testCapabilities();
		testTerminologyCapabilities();
		testCodeSystemSearch();

		testLookup(FhirTest.of("ICD-11 MMS lookup, default language")
				.withSystem(ICD11_MMS_SYSTEM)
				.withCode(CODE_COVID19));
		testLookup(FhirTest.of("ICD-11 MMS lookup, all properties")
				.withSystem(ICD11_MMS_SYSTEM)
				.withCode(CODE_COVID19)
				.withProperties(List.of("*")));
		testLookup(FhirTest.of("ICD-11 MMS lookup, French")
				.withSystem(ICD11_MMS_SYSTEM)
				.withCode(CODE_COVID19)
				.withLanguage("fr"));
		testLookup(FhirTest.of("ICD-11 MMS lookup, Spanish")
				.withSystem(ICD11_MMS_SYSTEM)
				.withCode(CODE_COVID19)
				.withLanguage("es"));
		testLookup(FhirTest.of("ICF lookup b710 (joint mobility)")
				.withSystem(ICD11_ICF_SYSTEM)
				.withCode(CODE_ICF_B710));

		testValidateCode(FhirTest.of("Validate known-good ICD-11 code")
				.withSystem(ICD11_MMS_SYSTEM)
				.withCode(CODE_COVID19)
				.withExpectValid(true));
		testValidateCode(FhirTest.of("Validate known-bad code, expect invalid")
				.withSystem(ICD11_MMS_SYSTEM)
				.withCode(CODE_INVALID)
				.withExpectValid(false));

		testSubsumes(FhirTest.of("Subsumes: self-subsumption, expect equivalent")
				.withSystem(ICD11_MMS_SYSTEM)
				.withCodeA(CODE_COVID19)
				.withCodeB(CODE_COVID19));

		testValueSetExpand(FhirTest.of("Expand ICD-11 implicit VS, filter=COVID")
				.withValueSetUrl(ICD11_MMS_SYSTEM + "?fhir_vs")
				.withFilter("COVID"));
		testValueSetExpand(FhirTest.of("Expand isa/" + CODE_COVID19)
				.withValueSetUrl(ICD11_MMS_SYSTEM + "?fhir_vs=isa/" + CODE_COVID19));

		testValueSetValidateCode(FhirTest.of("VS validate-code: COVID-19 in ICD-11 implicit VS")
				.withValueSetUrl(ICD11_MMS_SYSTEM + "?fhir_vs")
				.withCode(CODE_COVID19)
				.withExpectValid(true));

		// GET-based calls
		testLookupGet(FhirTest.of("Lookup via Foundation URI (GET)")
				.withSystem(ICD11_FOUNDATION_SYSTEM)
				.withCode(CODE_FOUNDATION_CHOLERA));
		testLookupGet(FhirTest.of("MMS lookup with version (GET)")
				.withSystem(ICD11_MMS_SYSTEM)
				.withCode(CODE_CHOLERA)
				.withVersion(ICD11_VERSION));
		testLookupGet(FhirTest.of("Postcoordinated code lookup (GET)")
				.withSystem(ICD11_MMS_SYSTEM)
				.withCode(CODE_POSTCRD_MMS)
				.withVersion(ICD11_VERSION));
		testLookupGet(FhirTest.of("Multi-axis postcoordinated code lookup (GET)")
				.withSystem(ICD11_MMS_SYSTEM)
				.withCode(CODE_POSTCRD_MULTI_AXIS)
				.withVersion(ICD11_VERSION));
		testValidateCodeInstanceGet(FhirTest.of("Validate postcoordinated code — expect valid (GET)")
				.withInstanceId(ICD11_MMS_CS_ID)
				.withCode(CODE_POSTCRD_VALID)
				.withExpectValid(true));
		testValidateCodeInstanceGet(FhirTest.of("Validate postcoordinated code — expect invalid (GET)")
				.withInstanceId(ICD11_MMS_CS_ID)
				.withCode(CODE_POSTCRD_INVALID)
				.withExpectValid(false));
		testValueSetExpandGet(FhirTest.of("Expand postcoordination scale: associatedWith (GET)")
				.withValueSetUrl(VS_URL_POSTCRD_ASSOC_WITH)
				.withVersion(ICD11_VERSION));
		testValueSetValidateCodeGet(FhirTest.of("VS validate-code on infectiousAgent postcoord scale (GET)")
				.withValueSetUrl(VS_URL_POSTCRD_INFECTIOUS)
				.withSystem(ICD11_MMS_SYSTEM)
				.withCode(CODE_INFECTIOUS_AGENT)
				.withVersion(ICD11_VERSION)
				.withExpectValid(true));

		testValueSetValidateCodePost(FhirTest.of("VS validate-code POST: BA00 is in inline VS")
				.withSystem(ICD11_MMS_SYSTEM)
				.withCode(CODE_COVID19)
				.withValueSetCodes(List.of(CODE_COVID19))
				.withExpectValid(true));
		testValueSetValidateCodePost(FhirTest.of("VS validate-code POST: invalid code absent from inline VS")
				.withSystem(ICD11_MMS_SYSTEM)
				.withCode(CODE_INVALID)
				.withValueSetCodes(List.of(CODE_COVID19))
				.withExpectValid(false));
	}

	// ── test methods ──────────────────────────────────────────────────────

	private void testTerminologyCapabilities() throws TermServerScriptException {
		FhirTest test = FhirTest.of("Terminology capability statement")
				.withSystem(secondaryServerUrl);
		test.setOperation("/metadata?mode=terminology");
		try {
			TerminologyCapabilities tc = fhirClient.getClient()
					.fetchResourceFromUrl(TerminologyCapabilities.class,
							secondaryServerUrl + "/metadata?mode=terminology");
			test.setRawJson(toJson(tc));
			String summary = "Terminology capabilities retrieved";
			if (tc.hasClosure()) {
				summary += ", closure supported";
			}
			test.setResponseSummary(summary);
			test.setPassed(true);
		} catch (Exception e) {
			test.setResponseSummary(e.getMessage());
			test.setPassed(false);
		}
		reportResult(test);
	}

	private void testCodeSystemSearch() throws TermServerScriptException {
		FhirTest test = FhirTest.of("List available CodeSystems")
				.withSystem(secondaryServerUrl);
		test.setOperation("/CodeSystem");
		try {
			Bundle bundle = fhirClient.getClient()
					.search()
					.forResource(CodeSystem.class)
					.returnBundle(Bundle.class)
					.execute();
			test.setRawJson(toJson(bundle));
			String systems = bundle.getEntry().stream()
					.filter(e -> e.getResource() instanceof CodeSystem)
					.map(e -> ((CodeSystem) e.getResource()).getUrl())
					.collect(Collectors.joining("; "));
			test.setResponseSummary("total=" + bundle.getTotal() + ": " + systems);
			test.setPassed(true);
		} catch (Exception e) {
			test.setResponseSummary(e.getMessage());
			test.setPassed(false);
		}
		reportResult(test);
	}

	private void testSubsumes(FhirTest test) throws TermServerScriptException {
		test.setOperation("CodeSystem/$subsumes");
		try {
			Parameters inParams = new Parameters();
			inParams.addParameter("system", new UriType(test.getSystem()));
			inParams.addParameter("codeA", new CodeType(test.getCodeA()));
			inParams.addParameter("codeB", new CodeType(test.getCodeB()));
			Parameters result = fhirClient.getClient()
					.operation()
					.onType(CodeSystem.class)
					.named("$subsumes")
					.withParameters(inParams)
					.returnResourceType(Parameters.class)
					.execute();
			test.setRawJson(toJson(result));
			test.setResponseSummary("outcome: " + extractString(result, "outcome"));
			test.setPassed(true);
		} catch (Exception e) {
			test.setResponseSummary(e.getMessage());
			test.setPassed(false);
		}
		reportResult(test);
	}

	private void testValueSetExpand(FhirTest test) throws TermServerScriptException {
		test.setOperation("ValueSet/$expand");
		try {
			Parameters inParams = new Parameters();
			inParams.addParameter("url", new UriType(test.getValueSetUrl()));
			if (test.getFilter() != null) {
				inParams.addParameter("filter", new StringType(test.getFilter()));
			}
			inParams.addParameter("count", new IntegerType(10));
			ValueSet result = fhirClient.getClient()
					.operation()
					.onType(ValueSet.class)
					.named("$expand")
					.withParameters(inParams)
					.returnResourceType(ValueSet.class)
					.execute();
			applyExpandResult(test, result);
		} catch (Exception e) {
			test.setResponseSummary(e.getMessage());
			test.setPassed(false);
		}
		reportResult(test);
	}

	private void applyExpandResult(FhirTest test, ValueSet result) {
		test.setRawJson(toJson(result));
		int total = result.hasExpansion() ? result.getExpansion().getTotal() : 0;
		String first = result.hasExpansion() && !result.getExpansion().getContains().isEmpty()
				? result.getExpansion().getContains().get(0).getCode()
				+ " " + result.getExpansion().getContains().get(0).getDisplay()
				: "(empty)";
		test.setResponseSummary("total=" + total + ", first: " + first);
		test.setPassed(true);
	}

	private void testLookupGet(FhirTest test) throws TermServerScriptException {
		test.setOperation("CodeSystem/$lookup");
		try {
			Parameters inParams = new Parameters();
			inParams.addParameter("system", new UriType(test.getSystem()));
			inParams.addParameter("code", new CodeType(test.getCode()));
			if (test.getVersion() != null) {
				inParams.addParameter("version", new StringType(test.getVersion()));
			}
			if (test.getLanguage() != null) {
				inParams.addParameter("displayLanguage", new CodeType(test.getLanguage()));
			}
			Parameters result = fhirClient.getClient()
					.operation()
					.onType(CodeSystem.class)
					.named("$lookup")
					.withParameters(inParams)
					.useHttpGet()
					.returnResourceType(Parameters.class)
					.execute();
			test.setRawJson(toJson(result));
			test.setResponseSummary("display: " + extractString(result, "display"));
			test.setPassed(true);
		} catch (Exception e) {
			test.setResponseSummary(e.getMessage());
			test.setPassed(false);
		}
		reportResult(test);
	}

	private void testValidateCodeInstanceGet(FhirTest test) throws TermServerScriptException {
		test.setOperation("CodeSystem/$validate-code (instance)");
		try {
			Parameters inParams = new Parameters();
			inParams.addParameter("code", new CodeType(test.getCode()));
			Parameters result = fhirClient.getClient()
					.operation()
					.onInstance(new IdType("CodeSystem", test.getInstanceId()))
					.named("$validate-code")
					.withParameters(inParams)
					.useHttpGet()
					.returnResourceType(Parameters.class)
					.execute();
			applyValidateCodeResult(test, result);
		} catch (Exception e) {
			test.setResponseSummary(e.getMessage());
			test.setPassed(false);
		}
		reportResult(test);
	}

	private void testValueSetExpandGet(FhirTest test) throws TermServerScriptException {
		test.setOperation("ValueSet/$expand");
		try {
			Parameters inParams = new Parameters();
			inParams.addParameter("url", new UriType(test.getValueSetUrl()));
			if (test.getVersion() != null) {
				inParams.addParameter("valueSetVersion", new StringType(test.getVersion()));
			}
			if (test.getFilter() != null) {
				inParams.addParameter("filter", new StringType(test.getFilter()));
			}
			ValueSet result = fhirClient.getClient()
					.operation()
					.onType(ValueSet.class)
					.named("$expand")
					.withParameters(inParams)
					.useHttpGet()
					.returnResourceType(ValueSet.class)
					.execute();
			applyExpandResult(test, result);
		} catch (Exception e) {
			test.setResponseSummary(e.getMessage());
			test.setPassed(false);
		}
		reportResult(test);
	}

	private void testValueSetValidateCodeGet(FhirTest test) throws TermServerScriptException {
		test.setOperation("ValueSet/$validate-code");
		try {
			Parameters inParams = new Parameters();
			inParams.addParameter("url", new UriType(test.getValueSetUrl()));
			inParams.addParameter("system", new UriType(test.getSystem()));
			inParams.addParameter("code", new CodeType(test.getCode()));
			if (test.getVersion() != null) {
				inParams.addParameter("valueSetVersion", new StringType(test.getVersion()));
			}
			Parameters result = fhirClient.getClient()
					.operation()
					.onType(ValueSet.class)
					.named("$validate-code")
					.withParameters(inParams)
					.useHttpGet()
					.returnResourceType(Parameters.class)
					.execute();
			applyValidateCodeResult(test, result);
		} catch (Exception e) {
			test.setResponseSummary(e.getMessage());
			test.setPassed(false);
		}
		reportResult(test);
	}

	private void testValueSetValidateCodePost(FhirTest test) throws TermServerScriptException {
		test.setOperation("ValueSet/$validate-code (inline VS)");
		try {
			ValueSet.ConceptSetComponent include = new ValueSet.ConceptSetComponent()
					.setSystem(test.getSystem());
			for (String code : test.getValueSetCodes()) {
				include.addConcept(new ValueSet.ConceptReferenceComponent().setCode(code));
			}
			ValueSet vs = new ValueSet();
			vs.setStatus(Enumerations.PublicationStatus.ACTIVE);
			vs.setCompose(new ValueSet.ValueSetComposeComponent().addInclude(include));

			Parameters inParams = new Parameters();
			inParams.addParameter().setName("valueSet").setResource(vs);
			inParams.addParameter("system", new UriType(test.getSystem()));
			inParams.addParameter("code", new CodeType(test.getCode()));

			Parameters result = fhirClient.getClient()
					.operation()
					.onType(ValueSet.class)
					.named("$validate-code")
					.withParameters(inParams)
					.returnResourceType(Parameters.class)
					.execute();
			applyValidateCodeResult(test, result);
		} catch (Exception e) {
			test.setResponseSummary(e.getMessage());
			test.setPassed(false);
		}
		reportResult(test);
	}

	private void testValueSetValidateCode(FhirTest test) throws TermServerScriptException {
		test.setOperation("ValueSet/$validate-code");
		try {
			Parameters inParams = new Parameters();
			inParams.addParameter("url", new UriType(test.getValueSetUrl()));
			inParams.addParameter("code", new CodeType(test.getCode()));
			Parameters result = fhirClient.getClient()
					.operation()
					.onType(ValueSet.class)
					.named("$validate-code")
					.withParameters(inParams)
					.returnResourceType(Parameters.class)
					.execute();
			applyValidateCodeResult(test, result);
		} catch (Exception e) {
			test.setResponseSummary(e.getMessage());
			test.setPassed(false);
		}
		reportResult(test);
	}

	private void applyValidateCodeResult(FhirTest test, Parameters result) {
		test.setRawJson(toJson(result));
		boolean resultValid = extractBoolean(result, "result");
		String display = extractString(result, "display");
		test.setResponseSummary("result=" + resultValid
				+ (!display.isEmpty() ? ", display: " + display : ""));
		test.setPassed(test.getExpectValid() == null || resultValid == test.getExpectValid());
	}

}
