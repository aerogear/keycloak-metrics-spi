package org.jboss.aerogear.keycloak.metrics;

import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;

public class MetricsEventListener implements EventListenerProvider {

    public final static String ID = "metrics-listener";

    private final static Logger logger = Logger.getLogger(MetricsEventListener.class);

    @Override
    public void onEvent(Event event) {
        logEventDetails(event);
        PrometheusExporter.instance().recordEvent(event);
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        logAdminEventDetails(event);
        PrometheusExporter.instance().recordAdminEvent(event);
    }

    private void logEventDetails(Event event) {
        logger.infof("Received user event of type %s in realm %s",
            (event.getType() == null ? "" : event.getType().name()),
            event.getRealmId());
    }

    private void logAdminEventDetails(AdminEvent event) {
        logger.infof("Received admin event of type %s (%s) in realm %s",
            (event.getOperationType() == null ? "" : event.getOperationType().name()),
            (event.getResourceType() == null ? "" : event.getResourceType().name()),
            event.getRealmId());
    }

    @Override
    public void close() {
        // unused
    }
}
