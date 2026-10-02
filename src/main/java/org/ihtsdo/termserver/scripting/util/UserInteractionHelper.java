package org.ihtsdo.termserver.scripting.util;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Project;
import org.ihtsdo.termserver.scripting.TermServerScript;

import java.util.Arrays;
import java.util.List;

import static org.snomed.otf.script.Script.print;

/**
 * Common questions for interactive scripts.  Pressing return always accepts the default shown in square brackets.
 * All questions read through the one script's STDIN, so several helpers (or a cloned script such as an importer)
 * don't end up with competing Scanners on System.in.
 */
public class UserInteractionHelper {

	private final TermServerScript script;

	public UserInteractionHelper(TermServerScript script) {
		this.script = script;
	}

	/**
	 * @param question without a trailing "?" - eg "Export unpromoted changes from BE5"
	 */
	public boolean askYesNo(String question, boolean defaultYes) {
		print(question + "? Y/N [" + (defaultYes ? "Y" : "N") + "]: ");
		String response = readLine();
		if (response.isEmpty()) {
			return defaultYes;
		}
		return response.equalsIgnoreCase("Y");
	}

	/**
	 * Ask for a value, accepting the default if return is pressed.  With no default, ask again until given a value.
	 */
	public String askWithDefault(String label, String defaultValue) {
		String safeDefault = defaultValue == null ? "" : defaultValue;
		String response = "";
		while (response.isEmpty()) {
			print(label + " [" + safeDefault + "]: ");
			response = readLine();
			if (response.isEmpty()) {
				response = safeDefault;
			}
		}
		return response;
	}

	/**
	 * Ask until one of the given single-letter choices is entered (no default - for decisions that need a deliberate
	 * answer, such as rolling back a commit).
	 * @return the choice, upper case
	 */
	public String askChoice(String prompt, String... choices) {
		List<String> allowed = Arrays.stream(choices).map(String::toUpperCase).toList();
		String response = "";
		while (!allowed.contains(response)) {
			print(prompt + ": ");
			response = readLine().toUpperCase();
		}
		return response;
	}

	/**
	 * Ask for a value that may be left blank, in which case an empty string is returned.
	 */
	public String askOptional(String label) {
		print(label + ": ");
		return readLine();
	}

	/**
	 * Offer the project given on the command line (-p), or ask for another, and make it the script's current project.
	 * @return the project now in use
	 */
	public Project confirmProject() throws TermServerScriptException {
		Project current = script.getProject();
		String currentKey = current == null ? null : current.getKey();
		if (currentKey != null && askYesNo("Use current project - " + currentKey, true)) {
			return current;
		}
		String projectKey = askWithDefault("Which project?", "");
		script.recoverProjectFromProjectName(projectKey);
		return script.getProject();
	}

	private String readLine() {
		return script.STDIN.nextLine().trim();
	}
}
