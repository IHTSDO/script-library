package org.ihtsdo.termserver.scripting.fhir;

import java.util.List;

public class FhirTest {

	// Input fields
	private String description;
	private String system;
	private String code;
	private String codeA;
	private String codeB;
	private String language;
	private List<String> properties;
	private String filter;
	private Boolean expectValid;
	private String valueSetUrl;
	private List<String> valueSetCodes;
	private String version;
	private String instanceId;

	// Result fields — set by test methods
	private String operation;
	private boolean passed;
	private String responseSummary;
	private String rawJson;

	public static FhirTest of(String description) {
		return new FhirTest().withDescription(description);
	}

	// ── fluent input setters ───────────────────────────────────────────────

	public FhirTest withDescription(String description) { this.description = description; return this; }
	public FhirTest withSystem(String system)           { this.system = system;           return this; }
	public FhirTest withCode(String code)               { this.code = code;               return this; }
	public FhirTest withCodeA(String codeA)             { this.codeA = codeA;             return this; }
	public FhirTest withCodeB(String codeB)             { this.codeB = codeB;             return this; }
	public FhirTest withLanguage(String language)       { this.language = language;       return this; }
	public FhirTest withProperties(List<String> properties) { this.properties = properties; return this; }
	public FhirTest withFilter(String filter)           { this.filter = filter;           return this; }
	public FhirTest withExpectValid(boolean expectValid){ this.expectValid = expectValid; return this; }
	public FhirTest withValueSetUrl(String valueSetUrl)         { this.valueSetUrl = valueSetUrl;   return this; }
	public FhirTest withValueSetCodes(List<String> valueSetCodes) { this.valueSetCodes = valueSetCodes; return this; }
	public FhirTest withVersion(String version)                   { this.version = version;             return this; }
	public FhirTest withInstanceId(String instanceId)             { this.instanceId = instanceId;       return this; }

	// ── result setters (package-private — only test methods write these) ──

	void setOperation(String operation)         { this.operation = operation; }
	void setPassed(boolean passed)              { this.passed = passed; }
	void setResponseSummary(String summary)     { this.responseSummary = summary; }
	void setRawJson(String rawJson)             { this.rawJson = rawJson; }

	// ── getters ───────────────────────────────────────────────────────────

	public String getDescription()      { return description != null ? description : ""; }
	public String getSystem()           { return system; }
	public String getCode()             { return code; }
	public String getCodeA()            { return codeA; }
	public String getCodeB()            { return codeB; }
	public String getLanguage()         { return language; }
	public List<String> getProperties() { return properties; }
	public String getFilter()           { return filter; }
	public Boolean getExpectValid()     { return expectValid; }
	public String getValueSetUrl()          { return valueSetUrl; }
	public List<String> getValueSetCodes()  { return valueSetCodes; }
	public String getVersion()              { return version; }
	public String getInstanceId()           { return instanceId; }
	public String getOperation()        { return operation != null ? operation : ""; }
	public boolean isPassed()           { return passed; }
	public String getResponseSummary()  { return responseSummary != null ? responseSummary : ""; }
	public String getRawJson()          { return rawJson != null ? rawJson : ""; }

	/** Computed display value for the Code/URL spreadsheet column. */
	public String getCodeDisplay() {
		if (code != null)                    return code;
		if (codeA != null && codeB != null)  return codeA + " / " + codeB;
		if (valueSetUrl != null)             return valueSetUrl;
		if (filter != null)                  return "filter=" + filter;
		return "";
	}

	/** Auto-generated notes from meaningful input fields (language, properties, expectValid). */
	public String getNotes() {
		StringBuilder sb = new StringBuilder();
		if (language != null) {
			sb.append("lang=").append(language);
		}
		if (properties != null) {
			if (sb.length() > 0) sb.append(", ");
			sb.append("properties=").append(String.join(",", properties));
		}
		if (expectValid != null) {
			if (sb.length() > 0) sb.append(", ");
			sb.append("expect ").append(expectValid ? "valid" : "invalid");
		}
		return sb.toString();
	}
}
