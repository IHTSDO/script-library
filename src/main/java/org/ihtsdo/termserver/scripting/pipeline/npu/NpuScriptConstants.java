package org.ihtsdo.termserver.scripting.pipeline.npu;

import org.ihtsdo.termserver.scripting.pipeline.ContentPipelineConstants;

public interface NpuScriptConstants extends ContentPipelineConstants {

	String NPU_PART_COMPONENT = "COMPONENT";
	String NPU_PART_PROPERTY = "PROPERTY";
	String NPU_PART_SCALE = "SCALE";
	String NPU_PART_UNIT = "UNIT";
	String NPU_PART_SYSTEM = "SYSTEM";

	int FILE_IDX_NPU_TECH_PREVIEW_CONCEPTS = 2;
	int FILE_IDX_NPU_FULL = 3;
	int FILE_IDX_NPU_PARTS = 4;
	int FILE_IDX_NPU_DETAIL = 5;

}
