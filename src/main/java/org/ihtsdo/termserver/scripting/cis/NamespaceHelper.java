package org.ihtsdo.termserver.scripting.cis;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Namespace;
import org.ihtsdo.termserver.scripting.client.CisClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class NamespaceHelper {

	private static final Logger LOGGER = LoggerFactory.getLogger(NamespaceHelper.class);

	// Static cache for all NamespaceHelpers
	public final Map<String, String> namespaceMap = new HashMap<>();

	private final CisClient cisClient;

	// Private constructor — enforce factory usage
	private NamespaceHelper(CisClient cisClient) {
		this.cisClient = Objects.requireNonNull(cisClient, "CisClient must not be null");
	}

	/**
	 * Factory method to create a NamespaceHelper.
	 *
	 * @param cisUrl  the URL of the CIS server
	 * @param cookie  authentication cookie for the CIS session
	 * @return a new NamespaceHelper instance with an internal CisClient
	 */
	public static NamespaceHelper create(String cisUrl, String cookie) {
		CisClient client = new CisClient(cisUrl, cookie);
		return new NamespaceHelper(client);
	}

	/**
	 * Returns the owner of the given namespace.
	 * Uses cache if available; otherwise fetches via CIS.
	 *
	 * @param namespace e.g., "1000023"
	 * @return owner name or null if unavailable
	 */
	public String getOwner(String namespaceId) {
		if (namespaceId == null || namespaceId.isEmpty()) {
			return null;
		}

		// Check cache first
		String owner = namespaceMap.get(namespaceId);
		if (owner != null) {
			return owner;
		}

		// Fetch from CIS if not cached
		try {
			Namespace namespace = cisClient.getNamespace(namespaceId);
			if (namespace == null) {
				owner = "Unknown Namespace";
			} else {
				owner = namespace.getOrganizationName();
			}
			namespaceMap.put(namespaceId, owner);
		} catch (Exception e) {
			LOGGER.error("Failed to retrieve owner for namespace {}", namespaceId, e);
		}
		return owner;
	}
}
