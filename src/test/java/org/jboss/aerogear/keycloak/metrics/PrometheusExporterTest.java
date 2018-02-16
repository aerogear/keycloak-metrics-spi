package org.jboss.aerogear.keycloak.metrics;

import io.prometheus.client.Counter;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.OperationType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

public class PrometheusExporterTest {

    @Before
    public void before() {
        for (Counter counter : PrometheusExporter.instance().counters.values()) {
            counter.clear();
        }
        PrometheusExporter.instance().totalLogins.clear();
        PrometheusExporter.instance().totalFailedLoginAttempts.clear();
        PrometheusExporter.instance().totalRegistrations.clear();
    }

    @Test
    public void shouldRegisterAllKeycloakEvents() {
        int userEvents = EventType.values().length;
        int adminEvents = OperationType.values().length;

        MatcherAssert.assertThat(
                "All events registered",
                userEvents + adminEvents - 3,                             // -3 comes from the events that
                is(PrometheusExporter.instance().counters.size()));       // have their own counters outside the counter map

    }

    @Test
    public void shouldCorrectlyCountLoginWhenIdentityProviderIsDefined() throws IOException {
        final Map<String, String> details1 = new HashMap<>();
        details1.put("identity_provider", "THE_ID_PROVIDER");
        final Event login1 = createEvent(EventType.LOGIN, details1);
        PrometheusExporter.instance().recordLogin(login1);
        assertMetric("keycloak_logins", "THE_ID_PROVIDER", 1);

        final Map<String, String> details2 = new HashMap<>();
        details2.put("identity_provider", "THE_ID_PROVIDER");
        final Event login2 = createEvent(EventType.LOGIN, details2);
        PrometheusExporter.instance().recordLogin(login2);
        assertMetric("keycloak_logins", "THE_ID_PROVIDER", 2);
    }

    @Test
    public void shouldCorrectlyCountLoginWhenIdentityProviderIsNotDefined() throws IOException {
        final Event login1 = createEvent(EventType.LOGIN);
        PrometheusExporter.instance().recordLogin(login1);
        assertMetric("keycloak_logins", "keycloak", 1);

        final Event login2 = createEvent(EventType.LOGIN);
        PrometheusExporter.instance().recordLogin(login2);
        assertMetric("keycloak_logins", "keycloak", 2);
    }

    @Test
    public void shouldCorrectlyCountLogin() throws IOException {
        // with id provider defined
        final Map<String, String> details1 = new HashMap<>();
        details1.put("identity_provider", "THE_ID_PROVIDER");
        final Event login1 = createEvent(EventType.LOGIN, details1);
        PrometheusExporter.instance().recordLogin(login1);
        assertMetric("keycloak_logins", "THE_ID_PROVIDER", 1);

        // without id provider defined
        final Event login2 = createEvent(EventType.LOGIN);
        PrometheusExporter.instance().recordLogin(login2);
        assertMetric("keycloak_logins", "keycloak", 1);
        assertMetric("keycloak_logins", "THE_ID_PROVIDER", 1);
    }

//    @Test
//    public void shouldCorrectlyCountLoginError() throws IOException {
//        // with id provider defined
//        final Map<String, String> details1 = new HashMap<>();
//        details1.put("identity_provider", "THE_ID_PROVIDER");
//        final Event event1 = createEvent(EventType.LOGIN_ERROR, details1);
//        event1.setError("user_not_found");
//        PrometheusExporter.instance().recordLoginError(event1);
//        assertMetric("keycloak_failed_login_attempts", "THE_ID_PROVIDER", 1);
//
//        // without id provider defined
//        final Event event2 = createEvent(EventType.LOGIN_ERROR);
//        PrometheusExporter.instance().recordLoginError(event2);
//        event2.setError("user_not_found");
//        assertMetric("keycloak_failed_login_attempts", "keycloak", 1);
//        assertMetric("keycloak_failed_login_attempts", "THE_ID_PROVIDER", 1);
//    }

    @Test
    public void shouldCorrectlyCountRegister() throws IOException {
        // with id provider defined
        final Map<String, String> details1 = new HashMap<>();
        details1.put("identity_provider", "THE_ID_PROVIDER");
        final Event event1 = createEvent(EventType.REGISTER, details1);
        PrometheusExporter.instance().recordRegistration(event1);
        assertMetric("keycloak_registrations", "THE_ID_PROVIDER", 1);

        // without id provider defined
        final Event event2 = createEvent(EventType.REGISTER);
        PrometheusExporter.instance().recordRegistration(event2);
        assertMetric("keycloak_registrations", "keycloak", 1);
        assertMetric("keycloak_registrations", "THE_ID_PROVIDER", 1);
    }

    private void assertMetric(String metricName, String provider, double metricValue) throws IOException {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            PrometheusExporter.instance().export(stream);
            String result = new String(stream.toByteArray());
            MatcherAssert.assertThat(result, containsString(metricName + "{realm=\"myrealm\",provider=\"" + provider + "\",} " + metricValue));
        }
    }

    private Event createEvent(EventType type, Map<String, String> details) {
        final Event event = new Event();
        event.setType(type);
        event.setRealmId("myrealm");
        if (details != null) {
            event.setDetails(details);
        } else {
            event.setDetails(Collections.emptyMap());
        }
        return event;
    }

    private Event createEvent(EventType type) {
        return createEvent(type, null);
    }
}
