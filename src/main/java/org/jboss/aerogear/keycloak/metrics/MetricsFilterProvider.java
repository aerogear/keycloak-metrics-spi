package org.jboss.aerogear.keycloak.metrics;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * This provider registers the MetricsFilter within environments that use Resteasy 4.x and above, e.g. Keycloak.X.
 */
@Provider
public class MetricsFilterProvider implements ContainerRequestFilter, ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) {
        MetricsFilter.instance().filter(requestContext);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        MetricsFilter.instance().filter(requestContext, responseContext);
    }
}
