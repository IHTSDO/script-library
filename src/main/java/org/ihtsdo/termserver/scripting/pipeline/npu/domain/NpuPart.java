package org.ihtsdo.termserver.scripting.pipeline.npu.domain;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import org.ihtsdo.termserver.scripting.pipeline.domain.Part;

public class NpuPart extends Part {

	@JacksonXmlProperty(localName = "created_date")
	private String createdDate;

	@JacksonXmlProperty(localName = "changed_date")
	private String changedDate;

	@JacksonXmlProperty(localName = "element_code")
	private String elementCode; // Will map to externalIdentifier

	@JacksonXmlProperty(localName = "element_term")
	private String elementTerm;  //Will map to partName

	@JacksonXmlProperty(localName = "abbreviated_element_term")
	private String abbreviatedElementTerm;

	@JacksonXmlProperty(localName = "reference_org")
	private String referenceOrg;

	@JacksonXmlProperty(localName = "reference_id")
	private String referenceId;

	@JacksonXmlProperty(localName = "local_definition")
	private String localDefinition;

	@JacksonXmlProperty(localName = "replaces")
	private String replaces;

	@JacksonXmlProperty(localName = "replaced_by")
	private String replacedBy;

	@JacksonXmlProperty(localName = "effective_from")
	private String effectiveFrom;

	@JacksonXmlProperty(localName = "effective_to")
	private String effectiveTo;

	@JacksonXmlProperty(localName = "active")
	private String active;

	@JacksonXmlProperty(localName = "current_version")
	private String currentVersion;

	// Getters and Setters

	public String getCreatedDate() {
		return createdDate;
	}

	public void setCreatedDate(String createdDate) {
		this.createdDate = createdDate;
	}

	public String getChangedDate() {
		return changedDate;
	}

	public void setChangedDate(String changedDate) {
		this.changedDate = changedDate;
	}

	public String getElementCode() {
		return elementCode;
	}

	public void setElementCode(String elementCode) {
		this.partNumber = elementCode;
		this.elementCode = elementCode;
	}

	public String getElementTerm() {
		return elementTerm;
	}

	public void setElementTerm(String elementTerm) {
		this.partName = elementTerm;
		this.elementTerm = elementTerm;
	}

	public String getAbbreviatedElementTerm() {
		return abbreviatedElementTerm;
	}

	public void setAbbreviatedElementTerm(String abbreviatedElementTerm) {
		this.abbreviatedElementTerm = abbreviatedElementTerm;
	}

	public String getReferenceOrg() {
		return referenceOrg;
	}

	public void setReferenceOrg(String referenceOrg) {
		this.referenceOrg = referenceOrg;
	}

	public String getReferenceId() {
		return referenceId;
	}

	public void setReferenceId(String referenceId) {
		this.referenceId = referenceId;
	}

	public String getLocalDefinition() {
		return localDefinition;
	}

	public void setLocalDefinition(String localDefinition) {
		this.localDefinition = localDefinition;
	}

	public String getReplaces() {
		return replaces;
	}

	public void setReplaces(String replaces) {
		this.replaces = replaces;
	}

	public String getReplacedBy() {
		return replacedBy;
	}

	public void setReplacedBy(String replacedBy) {
		this.replacedBy = replacedBy;
	}

	public String getEffectiveFrom() {
		return effectiveFrom;
	}

	public void setEffectiveFrom(String effectiveFrom) {
		this.effectiveFrom = effectiveFrom;
	}

	public String getEffectiveTo() {
		return effectiveTo;
	}

	public void setEffectiveTo(String effectiveTo) {
		this.effectiveTo = effectiveTo;
	}

	public String getActive() {
		return active;
	}

	public void setActive(String active) {
		this.active = active;
	}

	public String getCurrentVersion() {
		return currentVersion;
	}

	public void setCurrentVersion(String currentVersion) {
		this.currentVersion = currentVersion;
	}

	public boolean isCurrentVersion() {
		return currentVersion != null && currentVersion.equalsIgnoreCase("true");
	}
}

