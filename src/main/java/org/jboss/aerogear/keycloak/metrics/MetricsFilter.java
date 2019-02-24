package org.jboss.aerogear.keycloak.metrics;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;

public final class MetricsFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final String METRICS_REQUEST_TIMESTAMP = "metrics.requestTimestamp";
    private static final MetricsFilter INSTANCE = new MetricsFilter();

    public static MetricsFilter instance() {
        return INSTANCE;
    }

    private MetricsFilter() { }

    @Override
    public void filter(ContainerRequestContext req) {
        req.setProperty(METRICS_REQUEST_TIMESTAMP, System.currentTimeMillis());
    }

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) {
        String route = req.getUriInfo().getPath();
        int status = res.getStatus();

        // We are only interested in recording the response status if it was an error
        // (either a 4xx or 5xx). No point in counting  the successful responses
        if (status >= 400) {
            PrometheusExporter.instance().recordResponseError(status, req.getMethod(), route);
        }

        // Record request duration if timestamp property is present
        if (req.getProperty(METRICS_REQUEST_TIMESTAMP) != null) {
            long time = (long) req.getProperty(METRICS_REQUEST_TIMESTAMP);
            long dur = System.currentTimeMillis() - time;
            PrometheusExporter.instance().recordRequestDuration(dur, req.getMethod(), route);
        }
    }
}
