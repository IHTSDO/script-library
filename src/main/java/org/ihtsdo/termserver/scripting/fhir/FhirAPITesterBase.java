package org.ihtsdo.termserver.scripting.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.FhirTerser;
import ca.uhn.fhir.util.ParametersUtil;
import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

public abstract class FhirAPITesterBase extends TermServerReport implements ReportClass {

	protected static final String PASS = "PASS";
	protected static final String FAIL = "FAIL";

	protected FhirClient fhirClient;

	@Override
	public void init(JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId(GFOLDER_ADHOC_REPORTS);
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[]{
				"Description, Operation, Request URL, Request Body, System, Code/URL, Pass/Fail, Response Summary, Notes, Redirect Location, Response JSON, , , , , "};
		String[] tabNames = new String[]{"API Calls"};
		super.postInit(tabNames, columnHeadings, false);
		fhirClient = new FhirClient(secondaryServerUrl, getFhirContext());
	}

	protected abstract FhirContext getFhirContext();
	protected abstract void runTests() throws TermServerScriptException;

	// ── shared tests ──────────────────────────────────────────────────────

	@SuppressWarnings("unchecked")
	protected void testCapabilities() throws TermServerScriptException {
		FhirTest test = FhirTest.of("Server capability statement").withSystem(secondaryServerUrl);
		test.setOperation("/metadata");
		try {
			Class<? extends IBaseConformance> csClass = (Class<? extends IBaseConformance>)
					fhirClient.getFhirContext().getResourceDefinition("CapabilityStatement").getImplementingClass();
			IBaseConformance cs = fhirClient.getClient().capabilities().ofType(csClass).execute();
			test.setRawJson(toJson(cs));
			FhirTerser terser = fhirClient.getFhirContext().newTerser();
			String fhirVersion = terser.getSinglePrimitiveValueOrNull(cs, "fhirVersion");
			String software    = terser.getSinglePrimitiveValueOrNull(cs, "software.name");
			test.setResponseSummary("FHIR version: " + (fhirVersion != null ? fhirVersion : "unknown")
					+ ", software: " + (software != null ? software : "unknown"));
			test.setPassed(true);
		} catch (Exception e) {
			test.setResponseSummary(e.getMessage());
			test.setPassed(false);
		}
		reportResult(test);
	}

	protected void testLookup(FhirTest test) throws TermServerScriptException {
		test.setOperation("CodeSystem/$lookup");
		try {
			FhirContext ctx = fhirClient.getFhirContext();
			IBaseParameters inParams = newParameters();
			ParametersUtil.addParameterToParameters(ctx, inParams, "system", ParametersUtil.createUri(ctx, test.getSystem()));
			ParametersUtil.addParameterToParameters(ctx, inParams, "code",   ParametersUtil.createCode(ctx, test.getCode()));
			if (test.getLanguage() != null) {
				ParametersUtil.addParameterToParameters(ctx, inParams, "displayLanguage", ParametersUtil.createCode(ctx, test.getLanguage()));
			}
			if (test.getProperties() != null) {
				for (String property : test.getProperties()) {
					ParametersUtil.addParameterToParameters(ctx, inParams, "property", ParametersUtil.createCode(ctx, property));
				}
			}
			IBaseParameters result = fhirClient.getClient()
					.operation()
					.onType(getResourceClass("CodeSystem"))
					.named("$lookup")
					.withParameters(inParams)
					.execute();
			test.setRawJson(toJson((IBaseResource) result));
			test.setResponseSummary("display: " + extractString(result, "display"));
			test.setPassed(true);
		} catch (Exception e) {
			test.setResponseSummary(e.getMessage());
			test.setPassed(false);
		}
		reportResult(test);
	}

	protected void testValidateCode(FhirTest test) throws TermServerScriptException {
		test.setOperation("CodeSystem/$validate-code");
		try {
			FhirContext ctx = fhirClient.getFhirContext();
			IBaseParameters inParams = newParameters();
			ParametersUtil.addParameterToParameters(ctx, inParams, "url",  ParametersUtil.createUri(ctx, test.getSystem()));
			ParametersUtil.addParameterToParameters(ctx, inParams, "code", ParametersUtil.createCode(ctx, test.getCode()));
			IBaseParameters result = fhirClient.getClient()
					.operation()
					.onType(getResourceClass("CodeSystem"))
					.named("$validate-code")
					.withParameters(inParams)
					.execute();
			test.setRawJson(toJson((IBaseResource) result));
			boolean resultValid = extractBoolean(result, "result");
			String display = extractString(result, "display");
			String message = extractString(result, "message");
			test.setResponseSummary("result=" + resultValid
					+ (!display.isEmpty() ? ", display: " + display : "")
					+ (!message.isEmpty() ? ", message: " + message : ""));
			test.setPassed(test.getExpectValid() == null || resultValid == test.getExpectValid());
		} catch (Exception e) {
			test.setResponseSummary(e.getMessage());
			test.setPassed(false);
		}
		reportResult(test);
	}

	// ── shared helpers ────────────────────────────────────────────────────

	protected String toJson(IBaseResource resource) {
		return fhirClient.getFhirContext().newJsonParser().encodeResourceToString(resource);
	}

	protected String extractString(IBaseParameters params, String name) {
		return ParametersUtil.getNamedParameterValueAsString(fhirClient.getFhirContext(), params, name)
				.orElse("");
	}

	protected boolean extractBoolean(IBaseParameters params, String name) {
		return "true".equalsIgnoreCase(extractString(params, name));
	}

	@SuppressWarnings("unchecked")
	private <T extends IBaseResource> Class<T> getResourceClass(String resourceName) {
		return (Class<T>) fhirClient.getFhirContext().getResourceDefinition(resourceName).getImplementingClass();
	}

	private IBaseParameters newParameters() {
		return (IBaseParameters) fhirClient.getFhirContext().getResourceDefinition("Parameters").newInstance();
	}

	// ── reporting ─────────────────────────────────────────────────────────

	protected void reportResult(FhirTest test) throws TermServerScriptException {
		String requestDetail    = fhirClient != null ? fhirClient.getLastRequestDetail()    : null;
		String requestBody      = fhirClient != null ? fhirClient.getLastRequestBody()      : null;
		String redirectLocation = fhirClient != null ? fhirClient.getLastRedirectLocation() : null;
		report(PRIMARY_REPORT,
				test.getDescription(),
				test.getOperation(),
				requestDetail != null ? requestDetail : "",
				requestBody   != null ? requestBody   : "",
				test.getSystem(),
				test.getCodeDisplay(),
				test.isPassed() ? PASS : FAIL,
				test.getResponseSummary(),
				test.getNotes(),
				redirectLocation != null ? "301 → " + redirectLocation : "",
				test.getRawJson());
	}

	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("FHIR API Tester")
				.withDescription("Tests FHIR terminology operations against a target server")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(new JobParameters())
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		runTests();
	}
}
