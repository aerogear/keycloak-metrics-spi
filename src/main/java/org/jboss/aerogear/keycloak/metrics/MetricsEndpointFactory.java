package org.jboss.aerogear.keycloak.metrics;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

public class MetricsEndpointFactory implements RealmResourceProviderFactory {

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new MetricsEndpoint();
    }

    @Override
    public void init(Config.Scope config) {

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
