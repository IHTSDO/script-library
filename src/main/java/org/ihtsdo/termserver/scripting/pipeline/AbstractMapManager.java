package org.ihtsdo.termserver.scripting.pipeline;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.input.BOMInputStream;
import org.ihtsdo.otf.RF2Constants.Severity;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbstractMapManager implements ContentPipelineConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMapManager.class);

	protected static final String MAP_IMPORT = "Map Import";

	// Fixed header names
	public static final String COL_PART_NUM = "Source code";
	public static final String COL_STATUS = "Status";
	public static final String COL_NO_MAP = "No map flag";
	public static final String COL_TARGET = "Target code";

	protected ContentPipelineManager cpm;
	protected GraphLoader gl;
	//The map file line being processed, for reporting.  Zero when not loading a map file.
	private int currentLineNum = 0;

	protected AbstractMapManager(ContentPipelineManager cpm) {
		this.cpm = cpm;
		this.gl = cpm.getGraphLoader();
	}

	/**
	 * A single line of a map file, ready to be interpreted by the caller's own logic.
	 * The functional-interface / lambda replacement for what used to be called a "functoid".
	 */
	@FunctionalInterface
	protected interface LineProcessor {
		void process(String[] lineItems) throws TermServerScriptException;
	}

	/**
	 * Reads a tab-delimited map file: the first line is treated as a header (used to populate
	 * ColIdx), and every subsequent non-empty line is handed to lineProcessor. Problems reported
	 * by lineProcessor for an individual line are logged and counted rather than aborting the
	 * whole file; if any occurred, an exception is thrown once the whole file has been read.
	 */
	protected void loadMapFile(File mapFile, LineProcessor lineProcessor) throws TermServerScriptException {
		int lineNum = 0;
		try {
			LOGGER.info("Loading Map File: {}", mapFile);
			try (
					BOMInputStream bomIn = BOMInputStream.builder()
							.setInputStream(new FileInputStream(mapFile))
							// .setByteOrderMarks(...)   // optionally specify which BOMs to detect (defaults to UTF-8)
							.setInclude(false)           // whether to include the BOM in the stream or exclude it
							.get();
					InputStreamReader isr = new InputStreamReader(bomIn, StandardCharsets.UTF_8);
					BufferedReader br = new BufferedReader(isr)
			) {
				String line;
				while ((line = br.readLine()) != null) {
					lineNum++;
					if (lineNum == 1) {
						// Header line - discover indexes
						ColIdx.initialize(line);
					} else if (!line.isEmpty()) {
						currentLineNum = lineNum;
						processLine(lineProcessor, line, lineNum);
					}
				}
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to import " + mapFile + " at line " + lineNum, e);
		} finally {
			currentLineNum = 0;
		}

	}

	private void processLine(LineProcessor lineProcessor, String line, int lineNum) throws TermServerScriptException {
		String[] items = line.split("\t");
		String sourceNum = items[ColIdx.idx(COL_PART_NUM)];
		try {
			lineProcessor.process(items);
		} catch (TermServerScriptException e) {
			cpm.reportMapIssue(sourceNum, lineNum, Severity.HIGH, "Map line failed to load", e.getMessage());
		}
	}

	/**
	 * @return the map file line being processed, or blank if we're not loading a map file
	 */
	protected Object getCurrentLineNum() {
		return currentLineNum > 0 ? currentLineNum : "";
	}

	public static final class ColIdx {
		// Runtime-discovered indexes
		private static final Map<String, Integer> indexMap = new HashMap<>();

		private ColIdx() {
		}

		/** Discover column positions from header line */
		public static void initialize(String headerLine) {
			String[] headers = headerLine.split("\t", -1);
			for (int i = 0; i < headers.length; i++) {
				indexMap.put(headers[i].trim(), i);
			}
		}

		/** Retrieve index for a given column name */
		public static int idx(String columnName) {
			Integer idx = indexMap.get(columnName);
			if (idx == null) {
				throw new IllegalStateException("Column not found in header: " + columnName);
			}
			return idx;
		}
	}
}
