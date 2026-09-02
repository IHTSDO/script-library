package org.ihtsdo.termserver.scripting.msc;

public class MigrationEntry {

	private final String codeSystem;
	private final String moduleId;
	private final String version;
	private final MigrationStatus status;
	private final String source;
	private final String timeToProcess;

	public MigrationEntry(String codeSystem, String moduleId, String version, String status, String source, String timeToProcess) {
		this.codeSystem = codeSystem;
		this.moduleId = moduleId;
		this.version = version;
		this.status = MigrationStatus.fromFileValue(status);
		this.source = source;
		this.timeToProcess = timeToProcess;
	}

	public String getCodeSystem() {
		return codeSystem;
	}

	public String getModuleId() {
		return moduleId;
	}

	public String getVersion() {
		return version;
	}

	public MigrationStatus getStatus() {
		return status;
	}

	public String getSource() {
		return source;
	}

	public String getTimeToProcess() {
		return timeToProcess;
	}

	public String sourceBucket() {
		String withoutScheme = source.substring("s3://".length());
		return withoutScheme.substring(0, withoutScheme.indexOf('/'));
	}

	public String sourceKey() {
		String withoutScheme = source.substring("s3://".length());
		return withoutScheme.substring(withoutScheme.indexOf('/') + 1);
	}

	public String sourceFileName() {
		String key = sourceKey();
		return key.substring(key.lastIndexOf('/') + 1);
	}

	@Override
	public String toString() {
		return codeSystem + " " + moduleId + " " + version + " (" + status + ") -> " + source;
	}
}
