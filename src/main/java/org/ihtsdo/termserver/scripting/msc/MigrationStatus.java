package org.ihtsdo.termserver.scripting.msc;

public enum MigrationStatus {
	PENDING("-"),
	DONE("done"),
	FAILED("failed"),
	SKIPPED("skipped");  // Set manually in migration.txt to exclude an entry from processing

	private final String fileValue;

	MigrationStatus(String fileValue) {
		this.fileValue = fileValue;
	}

	public String getFileValue() {
		return fileValue;
	}

	public static MigrationStatus fromFileValue(String value) {
		for (MigrationStatus s : values()) {
			if (s.fileValue.equalsIgnoreCase(value)) {
				return s;
			}
		}
		throw new IllegalArgumentException("Unknown migration status: " + value);
	}

	@Override
	public String toString() {
		return name().toLowerCase();
	}
}
