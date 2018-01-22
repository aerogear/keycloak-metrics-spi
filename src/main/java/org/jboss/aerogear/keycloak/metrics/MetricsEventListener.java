package org.jboss.aerogear.keycloak.metrics;

import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;

public class MetricsEventListener implements EventListenerProvider {
  private final static Logger logger = Logger.getLogger(MetricsEventListener.class);
  public final static String ID = "metrics-listener";

  @Override
  public void onEvent(Event event) {
    logger.infof("Received user event of type %s in realm %s",
      event.getType().name(),
      event.getRealmId());

    switch (event.getType()) {
      case LOGIN:
      case IMPERSONATE:
        // Login and Impersonate both increase the number of currently
        // logged in users
        PrometheusExporter.instance().recordUserLogin(event);
        PrometheusExporter.instance().recordUserEvent(event);
        break;
      case LOGOUT:
        // Logout is the only action that decreases the number of currently
        // logged in users
        PrometheusExporter.instance().recordUserLogout(event);
        PrometheusExporter.instance().recordUserEvent(event);
        break;
      default:
        // Default action: record all other events in a generic way
        PrometheusExporter.instance().recordUserEvent(event);
        break;
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
