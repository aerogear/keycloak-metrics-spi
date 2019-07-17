package org.jboss.aerogear.keycloak.metrics;

import com.google.common.collect.ImmutableSet;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MediaType;
import java.util.Set;
import org.jboss.logging.Logger;

public final class MetricsFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final Logger LOG = Logger.getLogger(MetricsFilter.class);

    private static final String METRICS_REQUEST_TIMESTAMP = "metrics.requestTimestamp";
    private static final MetricsFilter INSTANCE = new MetricsFilter();

    // relevant response content types to be measured
    private static final Set<MediaType> CONTENT_TYPES = ImmutableSet.of(
        MediaType.APPLICATION_JSON_TYPE,
        MediaType.TEXT_HTML_TYPE,
        MediaType.APPLICATION_XML_TYPE
    );

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
        // and only if it is relevant (skip pictures)
        if (req.getProperty(METRICS_REQUEST_TIMESTAMP) != null &&
            contentTypeIsRelevant(res)) {
            long time = (long) req.getProperty(METRICS_REQUEST_TIMESTAMP);
            long dur = System.currentTimeMillis() - time;
            LOG.trace("Duration is calculated as " + dur + " ms.");
            PrometheusExporter.instance().recordRequestDuration(dur, req.getMethod(), route);
        }
    }

    private boolean contentTypeIsRelevant(ContainerResponseContext responseContext) {
        LOG.trace("Check if is response is relevant " + responseContext.getMediaType());
        boolean ret = responseContext.getMediaType() != null &&
            CONTENT_TYPES.stream().anyMatch(type -> type.isCompatible(responseContext.getMediaType()));
        LOG.trace("Result is " + ret);
        return ret;
    }
}
