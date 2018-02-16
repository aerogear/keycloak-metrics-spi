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

    private static final String DEFAULT_REALM = "myrealm";

    @Before
    public void before() {
        for (Counter counter : PrometheusExporter.instance().userEventCounters.values()) {
            counter.clear();
        }

        for (Counter counter : PrometheusExporter.instance().adminEventCounters.values()) {
            counter.clear();
        }
    }

    @Test
    public void shouldRegisterCountersForAllKeycloakUserEvents() {
        int userEvents = EventType.values().length;

        MatcherAssert.assertThat("All user events registered", userEvents, is(PrometheusExporter.instance().userEventCounters.size()));
    }

    @Test
    public void shouldRegisterCountersForAllKeycloakAdminEvents() {
        int adminEvents = OperationType.values().length;

        MatcherAssert.assertThat("All admin events registered", adminEvents, is(PrometheusExporter.instance().adminEventCounters.size()));
    }

    @Test
    public void shouldCorrectlyCountLoginWhenIdentityProviderIsDefined() throws IOException {
        final Event login1 = createEvent(EventType.LOGIN, tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordEvent(login1);
        assertMetric("keycloak_logins", 1, tuple("provider", "THE_ID_PROVIDER"));

        final Event login2 = createEvent(EventType.LOGIN, tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordEvent(login2);
        assertMetric("keycloak_logins", 2, tuple("provider", "THE_ID_PROVIDER"));
    }

    @Test
    public void shouldCorrectlyCountLoginWhenIdentityProviderIsNotDefined() throws IOException {
        final Event login1 = createEvent(EventType.LOGIN);
        PrometheusExporter.instance().recordEvent(login1);
        assertMetric("keycloak_logins", 1, tuple("provider", "keycloak"));

        final Event login2 = createEvent(EventType.LOGIN);
        PrometheusExporter.instance().recordEvent(login2);
        assertMetric("keycloak_logins", 2, tuple("provider", "keycloak"));
    }

    @Test
    public void shouldCorrectlyCountLoginsFromDifferentProviders() throws IOException {
        // with id provider defined
        final Event login1 = createEvent(EventType.LOGIN, tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordEvent(login1);
        assertMetric("keycloak_logins", 1, tuple("provider", "THE_ID_PROVIDER"));

        // without id provider defined
        final Event login2 = createEvent(EventType.LOGIN);
        PrometheusExporter.instance().recordEvent(login2);
        assertMetric("keycloak_logins", 1, tuple("provider", "keycloak"));
        assertMetric("keycloak_logins", 1, tuple("provider", "THE_ID_PROVIDER"));
    }

    @Test
    public void shouldRecordLoginsPerRealm() throws IOException {
        // realm 1
        final Event login1 = createEvent(EventType.LOGIN, DEFAULT_REALM, null, tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordEvent(login1);

        // realm 2
        final Event login2 = createEvent(EventType.LOGIN, "OTHER_REALM", null, tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordEvent(login2);

        assertMetric("keycloak_logins", 1, DEFAULT_REALM, tuple("provider", "THE_ID_PROVIDER"));
        assertMetric("keycloak_logins", 1, "OTHER_REALM", tuple("provider", "THE_ID_PROVIDER"));
    }

    @Test
    public void shouldCorrectlyCountLoginError() throws IOException {
        // with id provider defined
        final Event event1 = createEvent(EventType.LOGIN_ERROR, DEFAULT_REALM, "user_not_found", tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordEvent(event1);
        assertMetric("keycloak_failed_login_attempts", 1, tuple("provider", "THE_ID_PROVIDER"), tuple("error", "user_not_found"));

        // without id provider defined
        final Event event2 = createEvent(EventType.LOGIN_ERROR, DEFAULT_REALM, "user_not_found");
        PrometheusExporter.instance().recordEvent(event2);
        assertMetric("keycloak_failed_login_attempts", 1, tuple("provider", "keycloak"), tuple("error", "user_not_found"));
        assertMetric("keycloak_failed_login_attempts", 1, tuple("provider", "THE_ID_PROVIDER"), tuple("error", "user_not_found"));
    }

    @Test
    public void shouldCorrectlyCountRegister() throws IOException {
        // with id provider defined
        final Event event1 = createEvent(EventType.REGISTER, tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordEvent(event1);
        assertMetric("keycloak_registrations", 1, tuple("provider", "THE_ID_PROVIDER"));

        // without id provider defined
        final Event event2 = createEvent(EventType.REGISTER);
        PrometheusExporter.instance().recordEvent(event2);
        assertMetric("keycloak_registrations", 1, tuple("provider", "keycloak"));
        assertMetric("keycloak_registrations", 1, tuple("provider", "THE_ID_PROVIDER"));
    }

    @Test
    public void shouldCorrectlyRecordGenericEvents() throws IOException {
        final Event event1 = createEvent(EventType.UPDATE_EMAIL);
        PrometheusExporter.instance().recordEvent(event1);
        assertMetric("keycloak_user_event_UPDATE_EMAIL", 1);
        PrometheusExporter.instance().recordEvent(event1);
        assertMetric("keycloak_user_event_UPDATE_EMAIL", 2);


        final Event event2 = createEvent(EventType.REVOKE_GRANT);
        PrometheusExporter.instance().recordEvent(event2);
        assertMetric("keycloak_user_event_REVOKE_GRANT", 1);
        assertMetric("keycloak_user_event_UPDATE_EMAIL", 2);
    }

    @Test
    public void shouldCorrectlyRecordGenericAdminEvents() throws IOException {
        final AdminEvent event1 = new AdminEvent();
        event1.setOperationType(OperationType.ACTION);
        event1.setResourceType(ResourceType.AUTHORIZATION_SCOPE);
        event1.setRealmId(DEFAULT_REALM);
        PrometheusExporter.instance().recordAdminEvent(event1);
        assertMetric("keycloak_admin_event_ACTION", 1, tuple("resource", "AUTHORIZATION_SCOPE"));
        PrometheusExporter.instance().recordAdminEvent(event1);
        assertMetric("keycloak_admin_event_ACTION", 2, tuple("resource", "AUTHORIZATION_SCOPE"));


        final AdminEvent event2 = new AdminEvent();
        event2.setOperationType(OperationType.UPDATE);
        event2.setResourceType(ResourceType.CLIENT);
        event2.setRealmId(DEFAULT_REALM);
        PrometheusExporter.instance().recordAdminEvent(event2);
        assertMetric("keycloak_admin_event_UPDATE", 1, tuple("resource", "CLIENT"));
        assertMetric("keycloak_admin_event_ACTION", 2, tuple("resource", "AUTHORIZATION_SCOPE"));
    }

    private void assertMetric(String metricName, double metricValue, String realm, Tuple<String, String>... labels) throws IOException {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            PrometheusExporter.instance().export(stream);
            String result = new String(stream.toByteArray());

            final StringBuilder builder = new StringBuilder();

            builder.append(metricName).append("{");
            builder.append("realm").append("=\"").append(realm).append("\",");

            for (Tuple<String, String> label : labels) {
                builder.append(label.left).append("=\"").append(label.right).append("\",");
            }

            builder.append("} ").append(metricValue);

            MatcherAssert.assertThat(result, containsString(builder.toString()));
        }
    }

    private void assertMetric(String metricName, double metricValue, Tuple<String, String>... labels) throws IOException {
        this.assertMetric(metricName, metricValue, DEFAULT_REALM, labels);
    }

    private Event createEvent(EventType type, String realm, String error, Tuple<String, String>... tuples) {
        final Event event = new Event();
        event.setType(type);
        event.setRealmId(realm);
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
        return this.createEvent(type, DEFAULT_REALM, null, tuples);
    }

    private Event createEvent(EventType type) {
        return createEvent(type, DEFAULT_REALM, (String) null);
    }

    private static <L, R> Tuple<L, R> tuple(L left, R right) {
        return new Tuple<>(left, right);
    }

    private static final class Tuple<L, R> {
        final L left;
        final R right;

        private Tuple(L left, R right) {
            this.left = left;
            this.right = right;
        }
    }
}
