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
        for (Counter counter : PrometheusExporter.instance().counters.values()) {
            counter.clear();
        }
        PrometheusExporter.instance().totalLogins.clear();
        PrometheusExporter.instance().totalFailedLoginAttempts.clear();
        PrometheusExporter.instance().totalRegistrations.clear();
    }

    @Test
    public void shouldRegisterCountersForAllKeycloakEvents() {
        int userEvents = EventType.values().length;
        int adminEvents = OperationType.values().length;

        MatcherAssert.assertThat(
            "All events registered",
            userEvents + adminEvents - 3,                             // -3 comes from the events that
            is(PrometheusExporter.instance().counters.size()));       // have their own counters outside the counter map

    }

    @Test
    public void shouldCorrectlyCountLoginWhenIdentityProviderIsDefined() throws IOException {
        final Event login1 = createEvent(EventType.LOGIN, DEFAULT_REALM, "THE_CLIENT_ID", "user1",tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login1);
        assertMetric("keycloak_logins", 1, tuple("userId", "user1"), tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));

        final Event login2 = createEvent(EventType.LOGIN, DEFAULT_REALM, "THE_CLIENT_ID", tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login2);
        assertMetric("keycloak_logins", 1, tuple("userId", ""), tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));
    }

    @Test
    public void shouldCorrectlyCountLoginWhenIdentityProviderIsNotDefined() throws IOException {
        final Event login1 = createEvent(EventType.LOGIN,"");
        PrometheusExporter.instance().recordLogin(login1);
        assertMetric("keycloak_logins", 1, tuple("userId", ""), tuple("provider", "keycloak"), tuple("client_id", "THE_CLIENT_ID"));

        final Event login2 = createEvent(EventType.LOGIN,"");
        PrometheusExporter.instance().recordLogin(login2);
        assertMetric("keycloak_logins", 2, tuple("userId", ""), tuple("provider", "keycloak"), tuple("client_id", "THE_CLIENT_ID"));

        final Event login3 = createEvent(EventType.LOGIN,"user1");
        PrometheusExporter.instance().recordLogin(login3);
        assertMetric("keycloak_logins", 1, tuple("userId", "user1"), tuple("provider", "keycloak"), tuple("client_id", "THE_CLIENT_ID"));

        final Event login4 = createEvent(EventType.LOGIN,"user1");
        PrometheusExporter.instance().recordLogin(login4);
        assertMetric("keycloak_logins", 2, tuple("userId", "user1"), tuple("provider", "keycloak"), tuple("client_id", "THE_CLIENT_ID"));

        final Event login5 = createEvent(EventType.LOGIN,"user1");
        PrometheusExporter.instance().recordLogin(login5);
        assertMetric("keycloak_logins", 3, tuple("userId", "user1"), tuple("provider", "keycloak"), tuple("client_id", "THE_CLIENT_ID"));

        final Event login6 = createEvent(EventType.LOGIN,"user2");
        PrometheusExporter.instance().recordLogin(login6);
        assertMetric("keycloak_logins", 1, tuple("userId", "user2"), tuple("provider", "keycloak"), tuple("client_id", "THE_CLIENT_ID"));
    }

    @Test
    public void shouldCorrectlyCountLoginsFromDifferentProviders() throws IOException {
        // with id provider defined
        final Event login1 = createEvent(EventType.LOGIN, DEFAULT_REALM, "THE_CLIENT_ID", "user_login1", tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login1);
        assertMetric("keycloak_logins", 1, tuple("userId", "user_login1"), tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));

        // without id provider defined
        final Event login2 = createEvent(EventType.LOGIN, DEFAULT_REALM, "THE_CLIENT_ID", "user_login2");
        PrometheusExporter.instance().recordLogin(login2);
        assertMetric("keycloak_logins", 1, tuple("userId", "user_login2"), tuple("provider", "keycloak"), tuple("client_id", "THE_CLIENT_ID"));
        assertMetric("keycloak_logins", 1, tuple("userId", "user_login1"), tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));

        // with id provider defined
        final Event login3 = createEvent(EventType.LOGIN, DEFAULT_REALM, "THE_CLIENT_ID",  tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login3);
        assertMetric("keycloak_logins", 1, tuple("userId", ""), tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));

        // without id provider defined
        final Event login4 = createEvent(EventType.LOGIN, DEFAULT_REALM, "THE_CLIENT_ID");
        PrometheusExporter.instance().recordLogin(login4);
        assertMetric("keycloak_logins", 1, tuple("userId", ""), tuple("provider", "keycloak"), tuple("client_id", "THE_CLIENT_ID"));
    }

    @Test
    public void shouldRecordLoginsPerRealm() throws IOException {
        // realm 1
        final Event login1 = createEvent(EventType.LOGIN, DEFAULT_REALM, "THE_CLIENT_ID", "user_login1", tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login1);

        // realm 2
        final Event login2 = createEvent(EventType.LOGIN, "OTHER_REALM", "THE_CLIENT_ID",  tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login2);

        // realm 2
        final Event login3 = createEvent(EventType.LOGIN, "OTHER_REALM", "THE_CLIENT_ID",  tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login3);

        assertMetric("keycloak_logins", 1, DEFAULT_REALM, tuple("userId", "user_login1"), tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));
        assertMetric("keycloak_logins", 2, "OTHER_REALM", tuple("userId", "") , tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));
    }

    @Test
    public void shouldCorrectlyCountLoginError() throws IOException {
        // with id provider defined
        final Event event1 = createEvent(EventType.LOGIN_ERROR, DEFAULT_REALM, "THE_CLIENT_ID", "user_login1", "login_error",tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLoginError(event1);
        assertMetric("keycloak_failed_login_attempts", 1, tuple("userId", "user_login1"), tuple("provider", "THE_ID_PROVIDER"), tuple("error", "login_error"), tuple("client_id", "THE_CLIENT_ID"));

        // without id provider defined
        final Event event2 = createEvent(EventType.LOGIN_ERROR, DEFAULT_REALM, "THE_CLIENT_ID", "user_not_found", "login_failed");
        PrometheusExporter.instance().recordLoginError(event2);
        assertMetric("keycloak_failed_login_attempts", 1, tuple("userId", "user_not_found"), tuple("provider", "keycloak"), tuple("error", "login_failed"), tuple("client_id", "THE_CLIENT_ID"));
    }

    @Test
    public void shouldCorrectlyCountRegister() throws IOException {
        // with id provider defined
        final Event event1 = createEvent(EventType.REGISTER, DEFAULT_REALM, "THE_CLIENT_ID", "user1",tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordRegistration(event1);
        assertMetric("keycloak_registrations", 1, tuple("userId", "user1"), tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));

        // without id provider defined
        final Event event2 = createEvent(EventType.REGISTER, DEFAULT_REALM, "THE_CLIENT_ID","");
        PrometheusExporter.instance().recordRegistration(event2);
        assertMetric("keycloak_registrations", 1, tuple("userId", ""), tuple("provider", "keycloak"), tuple("client_id", "THE_CLIENT_ID"));
    }

    @Test
    public void shouldCorrectlyCountRegisterError() throws IOException {
        // with id provider defined
        final Event event1 = createEvent(EventType.REGISTER, DEFAULT_REALM, "THE_CLIENT_ID", "user1","registration_failed",tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordRegistrationError(event1);
        assertMetric("keycloak_registrations_errors", 1, tuple("userId", "user1"), tuple("provider", "THE_ID_PROVIDER"), tuple("error", "registration_failed"), tuple("client_id", "THE_CLIENT_ID"));

        // without id provider defined
        final Event event2 = createEvent(EventType.REGISTER, DEFAULT_REALM, "THE_CLIENT_ID","","registration_failed");
        PrometheusExporter.instance().recordRegistrationError(event2);
        assertMetric("keycloak_registrations_errors", 1, tuple("userId", ""), tuple("provider", "keycloak"), tuple("error", "registration_failed"), tuple("client_id", "THE_CLIENT_ID"));
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
    }

    @Test
    public void shouldCorrectlyRecordGenericAdminEvents() throws IOException {
        final AdminEvent event1 = new AdminEvent();
        event1.setOperationType(OperationType.ACTION);
        event1.setResourceType(ResourceType.AUTHORIZATION_SCOPE);
        event1.setRealmId(DEFAULT_REALM);
        PrometheusExporter.instance().recordGenericAdminEvent(event1);
        assertMetric("keycloak_admin_event_ACTION", 1, tuple("resource", "AUTHORIZATION_SCOPE"));
        PrometheusExporter.instance().recordGenericAdminEvent(event1);
        assertMetric("keycloak_admin_event_ACTION", 2, tuple("resource", "AUTHORIZATION_SCOPE"));


        final AdminEvent event2 = new AdminEvent();
        event2.setOperationType(OperationType.UPDATE);
        event2.setResourceType(ResourceType.CLIENT);
        event2.setRealmId(DEFAULT_REALM);
        PrometheusExporter.instance().recordGenericAdminEvent(event2);
        assertMetric("keycloak_admin_event_UPDATE", 1, tuple("resource", "CLIENT"));
    }

    @Test
    public void shouldCorrectlyRecordResponseDurations() throws IOException {
        PrometheusExporter.instance().recordRequestDuration(5, "GET", "/");
        assertGenericMetric("keycloak_request_duration_count", 1, tuple("method", "GET"), tuple("route", "/"));
        assertGenericMetric("keycloak_request_duration_sum", 5, tuple("method", "GET"), tuple("route", "/"));
    }

    @Test
    public void shouldCorrectlyRecordResponseErrors() throws IOException {
        PrometheusExporter.instance().recordResponseError(500, "POST", "/");
        assertGenericMetric("keycloak_response_errors", 1, tuple("code", "500"), tuple("method", "POST"), tuple("route", "/"));
    }

    @Test
    public void shouldTolerateNullLabels() throws IOException {
        final Event nullEvent = new Event();
        nullEvent.setClientId(null);
        nullEvent.setError(null);
        nullEvent.setRealmId(null);
        nullEvent.setUserId(null);
        PrometheusExporter.instance().recordLoginError(nullEvent);
        assertMetric("keycloak_failed_login_attempts", 1, "", tuple("userId", ""), tuple("provider", "keycloak"), tuple("error", ""), tuple("client_id", ""));
    }

    /**
     * Assert that Prometheus export contains a certain metric
     * @param metricName the metric name
     * @param metricValue the metric value
     * @param labels the list of labels
     * @throws IOException
     */
    private void assertGenericMetric(String metricName, double metricValue, Tuple<String, String>... labels) throws IOException {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            PrometheusExporter.instance().export(stream);
            String result = new String(stream.toByteArray());

            final StringBuilder builder = new StringBuilder();
            builder.append(metricName).append("{");

            for (Tuple<String, String> label : labels) {
                builder.append(label.left).append("=\"").append(label.right).append("\",");
            }

            builder.append("} ").append(metricValue);

            MatcherAssert.assertThat(result, containsString(builder.toString()));
        }
    }

    /**
     * Assert that Prometheus export contains a certain metric
     * @param metricName the metric name
     * @param metricValue the metric value
     * @param realm the realm name
     * @param labels the list of labels
     * @throws IOException
     */
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

    /**
     * Assert that Prometheus export contains a certain metric
     * @param metricName the metric name
     * @param metricValue the metric value
     * @param labels the list of labels
     * @throws IOException
     */
    private void assertMetric(String metricName, double metricValue, Tuple<String, String>... labels) throws IOException {
        this.assertMetric(metricName, metricValue, DEFAULT_REALM, labels);
    }

    /**
     * Create Event
     * @param type the EvenType
     * @param realm the realm name
     * @param clientId the client Id
     * @param userId the user Id
     * @param error the error
     * @param tuples the tuples
     * @return the event
     */
    private Event createEvent(EventType type, String realm, String clientId, String userId, String error, Tuple<String, String>... tuples) {
        final Event event = new Event();
        event.setType(type);
        event.setRealmId(realm);
        event.setClientId(clientId);
        event.setUserId(userId);
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

    /**
     * Create Event
     * @param type the EvenType
     * @param tuples the tuples
     * @return the event
     */
    private Event createEvent(EventType type, Tuple<String, String>... tuples) {
        return this.createEvent(type, DEFAULT_REALM, "THE_CLIENT_ID", null,  null, tuples);
    }

    /**
     * Create Event
     * @param type the EvenType
     * @param realm the realm name
     * @param clientId the client Id
     * @param tuples the tuples
     * @return the event
     */
    private Event createEvent(EventType type, String realm, String clientId, Tuple<String, String>... tuples) {
        return this.createEvent(type, realm, clientId, null,  null, tuples);
    }

    /**
     * Create Event
     * @param type the EvenType
     * @param realm the realm name
     * @param clientId the client Id
     * @param userId the user Id
     * @param tuples the tuples
     * @return the event
     */
    private Event createEvent(EventType type, String realm, String clientId, String userId, Tuple<String, String>... tuples) {
        return this.createEvent(type, realm, clientId, userId,  null, tuples);
    }
    /**
     * Create Event
     * @param type the EvenType
     * @param realm the realm name
     * @param tuples the tuples
     * @return the event
     */
    private Event createEvent(EventType type, String realm, Tuple<String, String>... tuples) {
        return this.createEvent(type, realm, "THE_CLIENT_ID", null,  null, tuples);
    }

    /**
     * Create Event
     * @param type the EvenType
     * @return the event
     */
    private Event createEvent(EventType type) {
        return this.createEvent(type, DEFAULT_REALM, "THE_CLIENT_ID", (String) null,  (String) null);
    }

    /**
     * Create Event
     * @param type the EvenType
     * @param userId the user Id
     * @return the event
     */
    private Event createEvent(EventType type, String userId) {
        return this.createEvent(type, DEFAULT_REALM, "THE_CLIENT_ID",  userId);
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
