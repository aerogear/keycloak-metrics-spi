package org.jboss.aerogear.keycloak.metrics;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

public class MetricsEndpointFactory implements RealmResourceProviderFactory {

    private static final String DISABLE_AUTHENTICATION = "disableAuthentication";
    private static final String BEARER_ENABLED_CONFIGURATION = "bearerEnabled";
    private static final String REALM_CONFIGURATION = "realm";
    private static final String DEFAULT_REALM = "master";
    private static final String ROLE_CONFIGURATION = "role";
    private static final String DEFAULT_ROLE = "prometheus-metrics";

    private Boolean authenticationDisabled;
    private Boolean bearerEnabled;
    private String realm;
    private String role;

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new MetricsEndpoint(session, this.authenticationDisabled, this.bearerEnabled, this.realm, this.role);
    }

    @Override
    public void init(Config.Scope config) {
        this.authenticationDisabled = config.getBoolean(DISABLE_AUTHENTICATION, false);
        this.bearerEnabled = config.getBoolean(BEARER_ENABLED_CONFIGURATION, false);
        this.realm = config.get(REALM_CONFIGURATION, DEFAULT_REALM);
        this.role = config.get(ROLE_CONFIGURATION, DEFAULT_ROLE);

        String resteasyVersion = ResteasyProviderFactory.class.getPackage().getImplementationVersion();
        if (resteasyVersion.startsWith("3.")) {
            // This registers the MetricsFilter within environments that use Resteasy < 4.x, e.g. Keycloak on Wildfly / JBossEAP
            registerMetricsFilterWithResteasy3();
        }

        // otherwise, we try to use the JAX-RS @Provider mechanism to register metrics filter
        // with Keycloak.X, see: MetricsFilterProvider
    }

    private void registerMetricsFilterWithResteasy3() {

        ResteasyProviderFactory providerFactory = ResteasyProviderFactory.getInstance();
        MetricsFilter filter = MetricsFilter.instance();

        providerFactory.getContainerRequestFilterRegistry().registerSingleton(filter);
        providerFactory.getContainerResponseFilterRegistry().registerSingleton(filter);
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // nothing to do
    }

    @Override
    public void close() {
        // nothing to close
    }

    @Override
    public String getId() {
        return MetricsEndpoint.ID;
    }
}
