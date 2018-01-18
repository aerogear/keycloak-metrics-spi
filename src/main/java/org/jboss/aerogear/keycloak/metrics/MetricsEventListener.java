package org.jboss.aerogear.keycloak.metrics;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;

public class MetricsEventListener implements EventListenerProvider {
  public final static String ID = "metrics-listener";

  @Override
  public void onEvent(Event event) {

  }

  @Override
  public void onEvent(AdminEvent event, boolean includeRepresentation) {

  }

  @Override
  public void close() {

  }
}
