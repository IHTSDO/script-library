package org.ihtsdo.termserver.scripting.msc;

import io.awspring.cloud.s3.InMemoryBufferingS3OutputStreamProvider;
import io.awspring.cloud.s3.PropertiesS3ObjectContentTypeResolver;
import io.awspring.cloud.s3.S3ProtocolResolver;
import org.ihtsdo.otf.dao.s3.S3Client;
import org.ihtsdo.otf.dao.s3.S3ClientImpl;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.resourcemanager.ManualResourceConfiguration;
import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.module.storage.ModuleMetadata;
import org.snomed.module.storage.ModuleStorageCoordinator;
import org.snomed.module.storage.ModuleStorageCoordinatorException;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MigrateToNewBucket extends TermServerScript {
	private static final Logger LOGGER = LoggerFactory.getLogger(MigrateToNewBucket.class);

	private static final String DESTINATION_BUCKET    = "snomed-versioned-content";
	private static final String MIGRATION_FILE_BUCKET = "developers-misc";
	private static final String MIGRATION_FILE_KEY    = "script-library/MigrateToNewBucket/migration.txt";

	private S3Client s3;
	private ModuleStorageCoordinator destMsc;
	private List<String> mscReadDirectories;

	public static void main(String[] args) throws TermServerScriptException {
		MigrateToNewBucket migrateToNewBucket = new MigrateToNewBucket();
		migrateToNewBucket.start();
	}

	private void start() {
		try (software.amazon.awssdk.services.s3.S3Client awsClient = software.amazon.awssdk.services.s3.S3Client.create()) {
			tryStart(awsClient);
		} catch (Exception e) {
			LOGGER.error("Failed migration", e);
		}
	}

	private void tryStart(software.amazon.awssdk.services.s3.S3Client awsClient) throws TermServerScriptException {
		// Check S3
		if (!testS3Connection(awsClient)) {
			LOGGER.error("Aborting migration due to S3 connection failure.");
			return;
		}

		s3 = new S3ClientImpl(awsClient);

		// Load migration entries
		List<MigrationEntry> migrationEntries = loadMigrationEntries();
		if (migrationEntries.isEmpty()) {
			LOGGER.info("No migration entries found.");
			return;
		}

		Map<MigrationStatus, Long> countsByStatus = migrationEntries.stream()
				.collect(Collectors.groupingBy(MigrationEntry::getStatus, Collectors.counting()));
		String breakdown = Arrays.stream(MigrationStatus.values())
				.filter(countsByStatus::containsKey)
				.map(s -> countsByStatus.get(s) + " " + s)
				.collect(Collectors.joining(", "));
		LOGGER.info("Loaded {} migration entries ({}).", migrationEntries.size(), breakdown);

		List<MigrationEntry> pending = migrationEntries.stream()
				.filter(e -> e.getStatus() == MigrationStatus.PENDING)
				.toList();
		if (pending.isEmpty()) {
			LOGGER.info("No pending entries to migrate.");
			return;
		}

		initMSC(DESTINATION_BUCKET, awsClient);
		int total = pending.size();
		for (int i = 0; i < total; i++) {
			LOGGER.info("Migrating entry {} of {}.", i + 1, total);
			migrateEntry(pending.get(i));
		}
	}

	private void migrateEntry(MigrationEntry entry) {
		String label = entry.getCodeSystem() + " " + entry.getVersion();
		LOGGER.info("Starting migration of {}...", label);
		try {
			ModuleMetadata existing = destMsc.getMetadata(entry.getCodeSystem(), entry.getModuleId(), entry.getVersion(), false);
			String dir = findDirectoryInDestination(entry.getCodeSystem(), entry.getModuleId(), entry.getVersion());
			String destPath = "s3://" + DESTINATION_BUCKET + "/" + dir + "/" + entry.getCodeSystem() + "_" + entry.getModuleId() + "/" + entry.getVersion() + "/" + existing.getFilename();
			LOGGER.info("... Already exists in destination ({}), skipping: {}", destPath, label);
			updateEntry(entry, MigrationStatus.DONE, null);
			return;
		} catch (ModuleStorageCoordinatorException.ResourceNotFoundException ignored) {
			// Not found — proceed with migration
		} catch (ModuleStorageCoordinatorException e) {
			LOGGER.error("Failed to check destination for {}: {}", label, e.getMessage());
			updateEntry(entry, MigrationStatus.FAILED, null);
			return;
		}
		Instant start = Instant.now();
		File tempFile = null;
		try {
			tempFile = downloadToTempFile(entry, s3);
			LOGGER.info("... Uploading to s3://{}/dev/{}_{}/{}/{}", DESTINATION_BUCKET, entry.getCodeSystem(), entry.getModuleId(), entry.getVersion(), entry.sourceFileName());
			destMsc.upload(entry.getCodeSystem(), entry.getModuleId(), entry.getVersion(), tempFile);
			LOGGER.info("... Finished migration of {}.", label);
			updateEntry(entry, MigrationStatus.DONE, Duration.between(start, Instant.now()));
		} catch (Exception e) {
			LOGGER.error("Failed to migrate {}: {}", label, e.getMessage(), e);
			updateEntry(entry, MigrationStatus.FAILED, Duration.between(start, Instant.now()));
		} finally {
			if (tempFile != null) {
				File tempDir = tempFile.getParentFile();
				if (!tempFile.delete()) {
					LOGGER.error("Failed to delete temp file: {}", tempFile.getAbsolutePath());
				}
				if (tempDir != null && !tempDir.delete()) {
					LOGGER.error("Failed to delete temp dir: {}", tempDir.getAbsolutePath());
				}
			}
		}
	}

	private File downloadToTempFile(MigrationEntry entry, S3Client s3) throws IOException {
		LOGGER.info("... Downloading s3://{}/{}", entry.sourceBucket(), entry.sourceKey());
		File tempDir = Files.createTempDirectory("migration-").toFile();
		File tempFile = new File(tempDir, entry.sourceFileName());
		try (ResponseInputStream<GetObjectResponse> stream = s3.getObject(entry.sourceBucket(), entry.sourceKey())) {
			Files.copy(stream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		LOGGER.info("... Downloaded {}.", humanReadableSize(tempFile.length()));
		return tempFile;
	}

	private ResourceLoader buildS3ResourceLoader(software.amazon.awssdk.services.s3.S3Client awsClient) {
		S3ProtocolResolver resolver = new S3ProtocolResolver(awsClient,
				new InMemoryBufferingS3OutputStreamProvider(awsClient, new PropertiesS3ObjectContentTypeResolver()));
		DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
		resourceLoader.addProtocolResolver(resolver);
		return resourceLoader;
	}

	private void initMSC(String bucket, software.amazon.awssdk.services.s3.S3Client awsClient) {
		ResourceLoader resourceLoader = buildS3ResourceLoader(awsClient);
		ResourceConfiguration.Cloud cloud = new ResourceConfiguration.Cloud(bucket, "");
		ManualResourceConfiguration config = new ManualResourceConfiguration(false, true, null, cloud);
		ResourceManager remoteStorageManager = new ResourceManager(config, resourceLoader, awsClient);
		String env = System.getProperty("MSC_ENVIRONMENT", "dev");
		switch (env) {
			case "prod" -> {
				destMsc = ModuleStorageCoordinator.initProd(remoteStorageManager);
				mscReadDirectories = List.of("prod");
			}
			case "uat" -> {
				destMsc = ModuleStorageCoordinator.initUat(remoteStorageManager);
				mscReadDirectories = List.of("uat", "prod");
			}
			default -> {
				destMsc = ModuleStorageCoordinator.initDev(remoteStorageManager);
				mscReadDirectories = List.of("dev", "prod");
			}
		}
		LOGGER.info("MSC initialised for environment: {}", env);
	}

	private boolean testS3Connection(software.amazon.awssdk.services.s3.S3Client awsClient) {
		LOGGER.info("Starting S3 connection test...");
		try {
			awsClient.headBucket(b -> b.bucket(DESTINATION_BUCKET));
			LOGGER.info("... Finished S3 connection test");
			return true;
		} catch (Exception e) {
			LOGGER.error("S3 connection failed: {}", e.getMessage());
			return false;
		}
	}

	private List<MigrationEntry> loadMigrationEntries() throws TermServerScriptException {
		List<MigrationEntry> entries = new ArrayList<>();
		Set<String> seenLines = new HashSet<>();
		Set<String> seenCodeSystemVersions = new HashSet<>();
		Set<String> seenSources = new HashSet<>();
		LOGGER.info("Loading migration entries from s3://{}/{}.", MIGRATION_FILE_BUCKET, MIGRATION_FILE_KEY);
		try (ResponseInputStream<GetObjectResponse> stream = s3.getObject(MIGRATION_FILE_BUCKET, MIGRATION_FILE_KEY);
			 BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line = reader.readLine(); // skip header
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				if (!seenLines.add(line)) {
					throw new TermServerScriptException("Duplicate line in migration file: " + line);
				}
				String[] parts = line.split("\\s+", 6);
				String codeSystemVersion = parts[0] + "/" + parts[2];
				if (!seenCodeSystemVersions.add(codeSystemVersion)) {
					throw new TermServerScriptException("Duplicate codeSystem/version in migration file: " + codeSystemVersion);
				}
				String source = parts.length >= 6 ? parts[5] : parts[4];
				if (!seenSources.add(source)) {
					throw new TermServerScriptException("Duplicate source in migration file: " + source);
				}
				String timeToProcess = parts.length >= 6 ? parts[4] : null;
				entries.add(new MigrationEntry(parts[0], parts[1], parts[2], parts[3], source, timeToProcess));
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to load migration file from s3://" + MIGRATION_FILE_BUCKET + "/" + MIGRATION_FILE_KEY, e);
		}
		return entries;
	}

	private void updateEntry(MigrationEntry entry, MigrationStatus newStatus, Duration elapsed) {
		String formattedTime = elapsed != null ? formatDuration(elapsed) : "-";
		try {
			List<String> lines;
			try (ResponseInputStream<GetObjectResponse> stream = s3.getObject(MIGRATION_FILE_BUCKET, MIGRATION_FILE_KEY);
				 BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
				lines = reader.lines().collect(Collectors.toList());
			}
			boolean updated = false;
			for (int i = 0; i < lines.size(); i++) {
				String[] parts = lines.get(i).trim().split("\\s+", 6);
				if (parts.length >= 5 && parts[0].equals(entry.getCodeSystem()) && parts[2].equals(entry.getVersion())) {
					String source = parts.length >= 6 ? parts[5] : parts[4];
					lines.set(i, parts[0] + " " + parts[1] + " " + parts[2] + " " + newStatus.getFileValue() + " " + formattedTime + " " + source);
					updated = true;
					break;
				}
			}
			if (updated) {
				byte[] content = String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
				s3.putObject(MIGRATION_FILE_BUCKET, MIGRATION_FILE_KEY, content);
				LOGGER.info("Updated {} {} to {} ({}) in migration file.", entry.getCodeSystem(), entry.getVersion(), newStatus, formattedTime);
			} else {
				LOGGER.warn("No matching entry found to update for {} {} in migration file.", entry.getCodeSystem(), entry.getVersion());
			}
		} catch (Exception e) {
			LOGGER.error("Failed to update migration file: {}", e.getMessage());
		}
	}

	private String humanReadableSize(long bytes) {
		if (bytes >= 1_073_741_824L) {
			return String.format("%.2f GB", bytes / 1_073_741_824.0);
		} else if (bytes >= 1_048_576L) {
			return String.format("%.1f MB", bytes / 1_048_576.0);
		} else {
			return bytes + " bytes";
		}
	}

	private String formatDuration(Duration d) {
		long hours = d.toHours();
		long minutes = d.toMinutesPart();
		long seconds = d.toSecondsPart();
		if (hours > 0) {
			return hours + "h" + minutes + "m" + seconds + "s";
		} else if (minutes > 0) {
			return minutes + "m" + seconds + "s";
		} else {
			return seconds + "s";
		}
	}

	private String findDirectoryInDestination(String codeSystem, String moduleId, String version) {
		for (String dir : mscReadDirectories) {
			String key = dir + "/" + codeSystem + "_" + moduleId + "/" + version + "/metadata.json";
			try {
				if (s3.exists(DESTINATION_BUCKET, key)) {
					return dir;
				}
			} catch (Exception ignored) {
			}
		}
		return "unknown";
	}

}
