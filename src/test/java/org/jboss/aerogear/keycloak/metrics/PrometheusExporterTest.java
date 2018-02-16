package org.jboss.aerogear.keycloak.metrics;

import io.prometheus.client.Counter;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

@SuppressWarnings("unchecked")
public class PrometheusExporterTest {

    private static final String MYREALM = "myrealm";

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
        final Event login1 = createEvent(EventType.LOGIN, Tuple.of("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login1);
        assertMetric("keycloak_logins", 1, Tuple.of("provider", "THE_ID_PROVIDER"));

        final Event login2 = createEvent(EventType.LOGIN, Tuple.of("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login2);
        assertMetric("keycloak_logins", 2, Tuple.of("provider", "THE_ID_PROVIDER"));
    }

    @Test
    public void shouldCorrectlyCountLoginWhenIdentityProviderIsNotDefined() throws IOException {
        final Event login1 = createEvent(EventType.LOGIN);
        PrometheusExporter.instance().recordLogin(login1);
        assertMetric("keycloak_logins", 1, Tuple.of("provider", "keycloak"));

        final Event login2 = createEvent(EventType.LOGIN);
        PrometheusExporter.instance().recordLogin(login2);
        assertMetric("keycloak_logins", 2, Tuple.of("provider", "keycloak"));
    }

    @Test
    public void shouldCorrectlyCountLogin() throws IOException {
        // with id provider defined
        final Event login1 = createEvent(EventType.LOGIN, Tuple.of("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login1);
        assertMetric("keycloak_logins", 1, Tuple.of("provider", "THE_ID_PROVIDER"));

        // without id provider defined
        final Event login2 = createEvent(EventType.LOGIN);
        PrometheusExporter.instance().recordLogin(login2);
        assertMetric("keycloak_logins", 1, Tuple.of("provider", "keycloak"));
        assertMetric("keycloak_logins", 1, Tuple.of("provider", "THE_ID_PROVIDER"));
    }

    @Test
    public void shouldCorrectlyCountLoginError() throws IOException {
        // with id provider defined
        final Event event1 = createEvent(EventType.LOGIN_ERROR, "user_not_found", Tuple.of("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLoginError(event1);
        assertMetric("keycloak_failed_login_attempts", 1, Tuple.of("provider", "THE_ID_PROVIDER"), Tuple.of("error", "user_not_found"));

        // without id provider defined
        final Event event2 = createEvent(EventType.LOGIN_ERROR, "user_not_found");
        PrometheusExporter.instance().recordLoginError(event2);
        assertMetric("keycloak_failed_login_attempts", 1, Tuple.of("provider", "keycloak"), Tuple.of("error", "user_not_found"));
        assertMetric("keycloak_failed_login_attempts", 1, Tuple.of("provider", "THE_ID_PROVIDER"), Tuple.of("error", "user_not_found"));
    }

    @Test
    public void shouldCorrectlyCountRegister() throws IOException {
        // with id provider defined
        final Event event1 = createEvent(EventType.REGISTER, Tuple.of("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordRegistration(event1);
        assertMetric("keycloak_registrations", 1, Tuple.of("provider", "THE_ID_PROVIDER"));

        // without id provider defined
        final Event event2 = createEvent(EventType.REGISTER);
        PrometheusExporter.instance().recordRegistration(event2);
        assertMetric("keycloak_registrations", 1, Tuple.of("provider", "keycloak"));
        assertMetric("keycloak_registrations", 1, Tuple.of("provider", "THE_ID_PROVIDER"));
    }

    @Test
    public void shouldCorrectlyRecordGenericEvents() throws IOException {
        final Event event1 = createEvent(EventType.UPDATE_EMAIL);
        PrometheusExporter.instance().recordGenericEvent(event1);
        assertMetric("keycloak_user_event_UPDATE_EMAIL", 1);
        PrometheusExporter.instance().recordGenericEvent(event1);
        assertMetric("keycloak_user_event_UPDATE_EMAIL", 2);


        final Event event2 = createEvent(EventType.REVOKE_GRANT);
        PrometheusExporter.instance().recordGenericEvent(event2);
        assertMetric("keycloak_user_event_REVOKE_GRANT", 1);
        assertMetric("keycloak_user_event_UPDATE_EMAIL", 2);
    }

    @Test
    public void shouldCorrectlyRecordGenericAdminEvents() throws IOException {
        final AdminEvent event1 = new AdminEvent();
        event1.setOperationType(OperationType.ACTION);
        event1.setResourceType(ResourceType.AUTHORIZATION_SCOPE);
        event1.setRealmId(MYREALM);
        PrometheusExporter.instance().recordGenericAdminEvent(event1);
        assertMetric("keycloak_admin_event_ACTION", 1, Tuple.of("resource", "AUTHORIZATION_SCOPE"));
        PrometheusExporter.instance().recordGenericAdminEvent(event1);
        assertMetric("keycloak_admin_event_ACTION", 2, Tuple.of("resource", "AUTHORIZATION_SCOPE"));


        final AdminEvent event2 = new AdminEvent();
        event2.setOperationType(OperationType.UPDATE);
        event2.setResourceType(ResourceType.CLIENT);
        event2.setRealmId(MYREALM);
        PrometheusExporter.instance().recordGenericAdminEvent(event2);
        assertMetric("keycloak_admin_event_UPDATE", 1, Tuple.of("resource", "CLIENT"));
        assertMetric("keycloak_admin_event_ACTION", 2, Tuple.of("resource", "AUTHORIZATION_SCOPE"));
    }

    // TODO: realm separation!

    private void assertMetric(String metricName, double metricValue, Tuple<String, String>... labels) throws IOException {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            PrometheusExporter.instance().export(stream);
            String result = new String(stream.toByteArray());

            final StringBuilder builder = new StringBuilder();

            builder.append(metricName).append("{realm=\"myrealm\",");

            for (Tuple<String, String> label : labels) {
                builder.append(label.left).append("=\"").append(label.right).append("\",");
            }

            builder.append("} ").append(metricValue);

            MatcherAssert.assertThat(result, containsString(builder.toString()));
        }
    }

    private Event createEvent(EventType type, String error, Tuple<String, String>... tuples) {
        final Event event = new Event();
        event.setType(type);
        event.setRealmId(MYREALM);
        if (tuples != null) {
            event.setDetails(new HashMap<>());
            for (Tuple<String, String> tuple : tuples) {
                event.getDetails().put(tuple.left, tuple.right);
            }
        } else {
            event.setDetails(Collections.emptyMap());
        }

        if (error != null) {
            event.setError(error);
        }
        return event;
    }

    private Event createEvent(EventType type, Tuple<String, String>... tuples) {
        return this.createEvent(type, null, tuples);
    }

    private Event createEvent(EventType type) {
        return createEvent(type, (String) null);
    }

    private static final class Tuple<L, R> {
        final L left;
        final R right;

        private Tuple(L left, R right) {
            this.left = left;
            this.right = right;
        }

        static <L, R> Tuple<L, R> of(L left, R right) {
            return new Tuple<>(left, right);
        }
    }
}
