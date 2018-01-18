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
        PrometheusExporter.instance().recordUserLogin(event);
        break;
      case LOGIN_ERROR:
      case IMPERSONATE_ERROR:
        PrometheusExporter.instance().recordFailedLogin(event);
      case LOGOUT:
        PrometheusExporter.instance().recordUserLogout(event);
      default:
        // Ignore other event types for now
        return;
    }
  }

  @Override
  public void onEvent(AdminEvent event, boolean includeRepresentation) {
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
