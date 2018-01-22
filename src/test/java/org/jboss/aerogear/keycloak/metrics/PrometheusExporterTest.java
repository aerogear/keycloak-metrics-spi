package org.jboss.aerogear.keycloak.metrics;

import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.OperationType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PrometheusExporterTest {
  private Event createEvent(EventType type) {
    final Event event = new Event();
    event.setType(type);
    event.setRealmId("myrealm");
    return event;
  }

  @Test
  public void shouldRegisterAllKeycloakEvents() {
    int userEvents = EventType.values().length;
    int adminEvents = OperationType.values().length;

    MatcherAssert.assertThat(
      "All events registered",
      userEvents + adminEvents == PrometheusExporter.counters.size());
  }

  @Test
  public void shouldCorrectlyCountLogin() throws IOException {
    final Event login = createEvent(EventType.LOGIN);
    PrometheusExporter.instance().recordUserLogin(login);
    assertLoggedInCount(1);
    final Event logout = createEvent(EventType.LOGOUT);
    PrometheusExporter.instance().recordUserLogout(logout);
    assertLoggedInCount(0);
  }

  @Test
  public void shouldCorrectlyCountImpersonate() throws IOException {
    final Event imporsonate = createEvent(EventType.IMPERSONATE);
    PrometheusExporter.instance().recordUserLogin(imporsonate);
    assertLoggedInCount(1);
    final Event logout = createEvent(EventType.LOGOUT);
    PrometheusExporter.instance().recordUserLogout(logout);
    assertLoggedInCount(0);
  }

  private void assertLoggedInCount(double number) throws IOException {
    try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
      PrometheusExporter.instance().export(stream);
      String result = new String(stream.toByteArray());
      MatcherAssert.assertThat("Logout count",
        result.contains("kc_logged_in_users{realm=\"myrealm\",} " + number));
    }
  }
}