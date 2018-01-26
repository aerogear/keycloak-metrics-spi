package org.jboss.aerogear.keycloak.metrics;

import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;

import java.util.Map;

public class MetricsEventListener implements EventListenerProvider {

    private final static Logger logger = Logger.getLogger(MetricsEventListener.class);
    public final static String ID = "metrics-listener";

    @Override
    public void onEvent(Event event) {
        logEventDetails(event);

        PrometheusExporter.instance().recordUserEvent(event);

        switch (event.getType()) {
            case LOGIN:
            case IMPERSONATE:
                PrometheusExporter.instance().recordUserLogin(event);
                break;
            case REGISTER:
                PrometheusExporter.instance().recordRegistration(event);
                break;
            case LOGIN_ERROR:
                PrometheusExporter.instance().recordLoginError(event);
                break;
        }
    }

    private void logEventDetails(Event event) {
        logger.infof("Received user event of type %s in realm %s",
                event.getType().name(),
                event.getRealmId());

        if (event.getDetails() != null) {
            logger.info("Event details:");
            for (Map.Entry<String, String> entry: event.getDetails().entrySet()) {
                logger.infof("<%s> : <%s>", entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        logger.infof("Received admin event of type %s (%s) in realm %s",
                event.getOperationType().name(),
                event.getResourceType().name(),
                event.getRealmId());

        PrometheusExporter.instance().recordAdminEvent(event);
    }

    @Override
    public void close() {
        // unused
    }
}
