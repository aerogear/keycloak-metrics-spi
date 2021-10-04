package org.jboss.aerogear.keycloak.metrics;

import org.jboss.logging.Logger;

import javax.ws.rs.core.UriInfo;
import java.util.List;

class ResourceExtractor {

    private final static Logger logger = Logger.getLogger(ResourceExtractor.class);

    private static final boolean IS_RESOURCE_SCRAPING_DISABLED = Boolean.getBoolean("RESOURCE_SCRAPING_DISABLED");
    private static final boolean URI_METRICS_ENABLED = Boolean.parseBoolean(System.getenv("URI_METRICS_ENABLED"));
    private static final boolean URI_METRICS_DETAILED = Boolean.parseBoolean(System.getenv("URI_METRICS_DETAILED"));
    private static final String URI_METRICS_FILTER = System.getenv("URI_METRICS_FILTER");

    private ResourceExtractor() {
    }

    /**
     * This method obtains a list of resource info from the {@link UriInfo} object.
     * <p>
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
     * <p>
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
                // every particular resource - just an aggregate with all other endpoints.
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

    /**
     * This method obtains a list of resource info from the {@link UriInfo} object and returns the resource URI.
     *
     * @param uriInfo {@link UriInfo} object obtained from JAX-RS
     * @return The resource uri.
     */
    static String getURI(UriInfo uriInfo) {
        if (URI_METRICS_ENABLED) {
            List<String> matchedURIs = uriInfo.getMatchedURIs();
            StringBuilder sb = new StringBuilder();

            if (URI_METRICS_FILTER != null && URI_METRICS_FILTER.length() != 0) {
                String[] filter = URI_METRICS_FILTER.split(",");

                for (int i = 0; i < filter.length; i++) {
                    if (matchedURIs.get(0).contains(filter[i])) {

                        sb = getURIDetailed(sb, matchedURIs);
                    }
                }
            } else {
                sb = getURIDetailed(sb, matchedURIs);
            }
            return sb.toString();
        }
        return "";
    }

    private static StringBuilder getURIDetailed(StringBuilder sb, List<String> matchedURIs) {

        String uri = matchedURIs.get(0);

        if (URI_METRICS_DETAILED) {
            sb.append(uri);
        } else {
            String[] realm = uri.split("/");
            if (realm.length != 1) {
                if (uri.startsWith("admin/realms/")) {
                    uri = uri.replace(realm[2], "{realm}");
                    if (realm.length > 4 && realm[3].equals("clients")) {
                        uri = uri.replace(realm[4], "{id}");
                    }
                }
                if (uri.startsWith("realms/")) {
                    uri = uri.replace(realm[1], "{realm}");
                }
            }
            sb.append(uri);
        }
        return sb;
    }
}
