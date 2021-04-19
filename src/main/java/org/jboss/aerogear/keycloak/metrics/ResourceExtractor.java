package org.jboss.aerogear.keycloak.metrics;

import org.jboss.logging.Logger;

import javax.ws.rs.core.UriInfo;
import java.util.List;

class ResourceExtractor {

    private final static Logger logger = Logger.getLogger(ResourceExtractor.class);

    private static final boolean IS_RESOURCE_SCRAPING_DISABLED = Boolean.getBoolean("RESOURCE_SCRAPING_DISABLED");


    private ResourceExtractor() {
    }

    /**
     * This method obtains a list of resource info from the {@link UriInfo} object.
     *
     * The algorithm extracts the last two components for the {@link UriInfo#getMatchedURIs()} apart from the
     * resources:
     * 13:28:52,766 INFO  [stdout] (default task-2) Matched URIs: [resources/89pe1/admin/logo-example/partials/client-list.html, resources]
     * 13:30:35,495 INFO  [stdout] (default task-3) Matched URIs: [realms/master/account, realms/master/account, realms]
     * 13:32:53,160 INFO  [stdout] (default task-10) Matched URIs: [realms/master/.well-known/openid-configuration, realms]
     * 13:33:16,656 INFO  [stdout] (default task-10) Matched URIs: [realms/master/protocol/openid-connect/auth, realms/master/protocol/openid-connect, realms]
     * 13:33:47,319 INFO  [stdout] (default task-10) Matched URIs: [realms/master/protocol/openid-connect/certs, realms/master/protocol/openid-connect, realms]
     * 13:34:37,768 INFO  [stdout] (default task-10) Matched URIs: [realms/master/account/applications, realms/master/account, realms]
     * 13:35:48,003 INFO  [stdout] (default task-11) Matched URIs: [admin/realms/master/client-scopes/ee5e5908-a0b6-43c8-b213-fb452f129a5e, admin/realms/master/client-scopes, admin/realms/master, admin/realms, admin]
     * 13:36:24,642 INFO  [stdout] (default task-11) Matched URIs: [admin/realms/master/users/171753bc-8184-4989-929b-288fdc661b90, admin/realms/master/users, admin/realms/master, admin/realms, admin]
     * 13:36:24,793 INFO  [stdout] (default task-11) Matched URIs: [admin/realms/master/attack-detection/brute-force/users/171753bc-8184-4989-929b-288fdc661b90, admin/realms/master/attack-detection, admin/realms/master, admin/realms, admin]
     *
     * The mechanism might be switched off by using IS_RESOURCE_SCRAPING_DISABLED environment variable.
     *
     * @param uriInfo {@link UriInfo} object obtained from JAX-RS
     * @return The resource name.
     */
    static String getResource(UriInfo uriInfo) {
        if (!IS_RESOURCE_SCRAPING_DISABLED) {
            List<String> matchedURIs = uriInfo.getMatchedURIs();
            if (matchedURIs.size() >= 2) {
                // A special case for all static resources - we're not interested in
                // evey particular resource - just an aggregate with all other endpoints.
                if ("resources".equals(matchedURIs.get(matchedURIs.size() - 1))) {
                    return "";
                }
                StringBuilder sb = new StringBuilder();
                sb.append(matchedURIs.get(matchedURIs.size() - 1));
                sb.append(",");
                sb.append(matchedURIs.get(matchedURIs.size() - 2));
                return sb.toString();
            }
        }
        return "";
    }

}
