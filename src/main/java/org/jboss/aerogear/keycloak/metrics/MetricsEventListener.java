package org.jboss.aerogear.keycloak.metrics;

import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.events.admin.OperationType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

public class MetricsEventListener implements EventListenerProvider {

    public final static String ID = "metrics-listener";

    private final static Logger logger = Logger.getLogger(MetricsEventListener.class);

    private KeycloakSession session;

    public MetricsEventListener (KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void onEvent(Event event) {
        logEventDetails(event);

        switch (event.getType()) {
            case LOGIN:
                PrometheusExporter.instance().recordLogin(event);
                break;
            case REGISTER:
                PrometheusExporter.instance().recordRegistration(event);
                countUsers(session.realms().getRealm(event.getRealmId()));
                break;
            case REGISTER_ERROR:
                PrometheusExporter.instance().recordRegistrationError(event);
                break;
            case LOGIN_ERROR:
                PrometheusExporter.instance().recordLoginError(event);
                break;
            default:
                PrometheusExporter.instance().recordGenericEvent(event);
        }
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        logAdminEventDetails(event);

        if (event.getResourceType() == ResourceType.USER && ( event.getOperationType() == OperationType.CREATE || event.getOperationType() == OperationType.DELETE)) {
            countUsers(session.realms().getRealm(event.getRealmId()));
        }

        PrometheusExporter.instance().recordGenericAdminEvent(event);
    }

    private void countUsers(RealmModel realm) {
        int count = session.users().getUsersCount(realm);
        PrometheusExporter.instance().recordCountUsers(realm.getId(), count);
    }

    private void logEventDetails(Event event) {
        logger.infof("Received user event of type %s in realm %s",
                event.getType().name(),
                event.getRealmId());
    }

    private void logAdminEventDetails(AdminEvent event) {
        logger.infof("Received admin event of type %s (%s) in realm %s",
                event.getOperationType().name(),
                event.getResourceType().name(),
                event.getRealmId());
    }

    @Override
    public void close() {
        // unused
    }
}
