package org.ihtsdo.termserver.scripting.template;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;

public class NormaliseConceptsDriven extends NormaliseConcepts {

	public NormaliseConceptsDriven(BatchFix clone) {
		super(clone);
	}
	
	public static void main(String[] args) throws TermServerScriptException {
		NormaliseConceptsDriven app = new NormaliseConceptsDriven(null);
		try {
			ReportSheetManager.setTargetFolderId(GFOLDER_QI_NORMALIZATION);
			app.selfDetermining = false;  //We expect to be given a file for 'driven' classes
			app.init(args);
			app.loadProjectSnapshot(false);  //Load all descriptions
			app.postInit();
			app.processFile();
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to NormaliseTemplateCompliantConcepts", e);
		} finally {
			app.finish();
		}
	}


	@Override
	public int normaliseConcept(Task t, Concept c, Concept newPPP) throws TermServerScriptException {
		//Have we specified a ppp in the issues field?
		newPPP = checkConceptForSpecifiedPPP(c);
		return super.normaliseConcept(t, c, newPPP);
	}

	protected Concept checkConceptForSpecifiedPPP(Concept loadedConcept) throws TermServerScriptException {
		Concept newPPP = null;
		//If we've just loaded this concept, we won't have the issues list populated, so switch back to the copy in memory
		Concept c = gl.getConcept(loadedConcept.getConceptId());
		if (c.getIssueList() != null && !c.getIssueList().isEmpty()) {
			String ppp = c.getIssueList().get(0);
			if (ppp != null && !ppp.isEmpty()) {
				newPPP = gl.getConcept(ppp);
				if (newPPP == null) {
					throw new TermServerScriptException("Specified PPP " + ppp + " not found in the terminology");
				}
			}
		}
		return newPPP;
	}

}
