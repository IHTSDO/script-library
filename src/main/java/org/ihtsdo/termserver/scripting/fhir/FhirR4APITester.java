package org.ihtsdo.termserver.scripting.fhir;

import ca.uhn.fhir.context.FhirContext;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

public class FhirR4APITester extends FhirAPITesterBase {

	private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();

	// Snowstorm FHIR R4 endpoint — update to target server URL
	private static final String SNOWSTORM_SERVER_URL = "http://localhost:8080/fhir";

	static final String SNOMED_SYSTEM = "http://snomed.info/sct";

	// Example test code: SNOMED CT concept for Clinical finding
	private static final String CODE_CLINICAL_FINDING = "404684003";

	@Override
	protected FhirContext getFhirContext() {
		return FHIR_CONTEXT;
	}

	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("FHIR R4 API Tester — Snowstorm")
				.withDescription("Exercises FHIR R4 terminology operations against the Snowstorm FHIR API")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(new JobParameters())
				.withTag(INT)
				.build();
	}

	@Override
	protected void runTests() throws TermServerScriptException {
		testCapabilities();
		testLookup(FhirTest.of("SNOMED CT clinical finding lookup")
				.withSystem(SNOMED_SYSTEM).withCode(CODE_CLINICAL_FINDING));
		testValidateCode(FhirTest.of("Validate known SNOMED CT code")
				.withSystem(SNOMED_SYSTEM).withCode(CODE_CLINICAL_FINDING).withExpectValid(true));
	}
}
