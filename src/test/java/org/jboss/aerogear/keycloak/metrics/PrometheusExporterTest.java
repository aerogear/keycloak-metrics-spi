package org.jboss.aerogear.keycloak.metrics;

import io.prometheus.client.CollectorRegistry;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class PrometheusExporterTest {

    private static final String DEFAULT_REALM_ID = "2af8c4d4-4d58-4d74-9ad7-eef9aac06a90";
    private static final String DEFAULT_REALM_NAME = "myrealm";

    private static final RealmProvider realmProvider = mock(RealmProvider.class);

    @Before
    public void setupRealmProvider() {
        RealmModel realm = mock(RealmModel.class);
        when(realm.getName()).thenReturn(DEFAULT_REALM_NAME);
        when(realmProvider.getRealm(eq(DEFAULT_REALM_ID))).thenReturn(realm);
        RealmModel otherRealm = mock(RealmModel.class);
        when(otherRealm.getName()).thenReturn("OTHER_REALM");
        when(realmProvider.getRealm(eq("OTHER_REALM_ID"))).thenReturn(otherRealm);
    }

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Before
    public void resetSingleton() throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field instance = PrometheusExporter.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, null);
        CollectorRegistry.defaultRegistry.clear();
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
    public void shouldCorrectlyCountLoginAttemptsForSuccessfulAndFailedAttempts() throws IOException {
        // with LOGIN event
        final Event login1 = createEvent(EventType.LOGIN, DEFAULT_REALM_ID, "THE_CLIENT_ID");
        PrometheusExporter.instance().recordLogin(login1, realmProvider);
        assertMetric("keycloak_login_attempts", 1, tuple("provider", "keycloak"), tuple("client_id", "THE_CLIENT_ID"));
        assertMetric("keycloak_logins", 1, tuple("provider", "keycloak"), tuple("client_id", "THE_CLIENT_ID"));

        // with LOGIN_ERROR event
        final Event event2 = createEvent(EventType.LOGIN_ERROR, DEFAULT_REALM_ID, "THE_CLIENT_ID", "user_not_found");
        PrometheusExporter.instance().recordLoginError(event2, realmProvider);
        assertMetric("keycloak_login_attempts", 2, tuple("provider", "keycloak"), tuple("client_id", "THE_CLIENT_ID"));
        assertMetric("keycloak_failed_login_attempts", 1, tuple("provider", "keycloak"), tuple("error", "user_not_found"), tuple("client_id", "THE_CLIENT_ID"));
    }

    @Test
    public void shouldCorrectlyCountLoginWhenIdentityProviderIsDefined() throws IOException {
        final Event login1 = createEvent(EventType.LOGIN, DEFAULT_REALM_ID, "THE_CLIENT_ID", tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login1, realmProvider);
        assertMetric("keycloak_logins", 1, tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));

        final Event login2 = createEvent(EventType.LOGIN, DEFAULT_REALM_ID, "THE_CLIENT_ID", tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login2, realmProvider);
        assertMetric("keycloak_logins", 2, tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));
    }

    @Test
    public void shouldCorrectlyCountLoginWhenIdentityProviderIsNotDefined() throws IOException {
        final Event login1 = createEvent(EventType.LOGIN);
        PrometheusExporter.instance().recordLogin(login1, realmProvider);
        assertMetric("keycloak_logins", 1, tuple("provider", "keycloak"), tuple("client_id", "THE_CLIENT_ID"));

        final Event login2 = createEvent(EventType.LOGIN);
        PrometheusExporter.instance().recordLogin(login2, realmProvider);
        assertMetric("keycloak_logins", 2, tuple("provider", "keycloak"), tuple("client_id", "THE_CLIENT_ID"));
    }

    @Test
    public void shouldCorrectlyCountLoginsFromDifferentProviders() throws IOException {
        // with id provider defined
        final Event login1 = createEvent(EventType.LOGIN, DEFAULT_REALM_ID, "THE_CLIENT_ID", tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login1, realmProvider);
        assertMetric("keycloak_logins", 1, tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));

        // without id provider defined
        final Event login2 = createEvent(EventType.LOGIN, DEFAULT_REALM_ID, "THE_CLIENT_ID");
        PrometheusExporter.instance().recordLogin(login2, realmProvider);
        assertMetric("keycloak_logins", 1, tuple("provider", "keycloak"), tuple("client_id", "THE_CLIENT_ID"));
        assertMetric("keycloak_logins", 1, tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));
    }

    @Test
    public void shouldRecordLoginsPerRealm() throws IOException {
        // realm 1
        final Event login1 = createEvent(EventType.LOGIN, DEFAULT_REALM_ID, "THE_CLIENT_ID", tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login1, realmProvider);

        // realm 2
        final Event login2 = createEvent(EventType.LOGIN, "OTHER_REALM_ID", "THE_CLIENT_ID", tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login2, realmProvider);

        assertMetric("keycloak_logins", 1, DEFAULT_REALM_NAME, tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));
        assertMetric("keycloak_logins", 1, "OTHER_REALM", tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));
    }

    @Test
    public void shouldCorrectlyCountLoginError() throws IOException {
        // with id provider defined
        final Event event1 = createEvent(EventType.LOGIN_ERROR, DEFAULT_REALM_ID, "THE_CLIENT_ID", "user_not_found", tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLoginError(event1, realmProvider);
        assertMetric("keycloak_failed_login_attempts", 1, tuple("provider", "THE_ID_PROVIDER"), tuple("error", "user_not_found"), tuple("client_id", "THE_CLIENT_ID"));

        // without id provider defined
        final Event event2 = createEvent(EventType.LOGIN_ERROR, DEFAULT_REALM_ID, "THE_CLIENT_ID", "user_not_found");
        PrometheusExporter.instance().recordLoginError(event2, realmProvider);
        assertMetric("keycloak_failed_login_attempts", 1, tuple("provider", "keycloak"), tuple("error", "user_not_found"), tuple("client_id", "THE_CLIENT_ID"));
        assertMetric("keycloak_failed_login_attempts", 1, tuple("provider", "THE_ID_PROVIDER"), tuple("error", "user_not_found"), tuple("client_id", "THE_CLIENT_ID"));
    }

    @Test
    public void shouldCorrectlyCountRegister() throws IOException {
        // with id provider defined
        final Event event1 = createEvent(EventType.REGISTER, DEFAULT_REALM_ID, "THE_CLIENT_ID", tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordRegistration(event1, realmProvider);
        assertMetric("keycloak_registrations", 1, tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));

        // without id provider defined
        final Event event2 = createEvent(EventType.REGISTER, DEFAULT_REALM_ID, "THE_CLIENT_ID");
        PrometheusExporter.instance().recordRegistration(event2, realmProvider);
        assertMetric("keycloak_registrations", 1, tuple("provider", "keycloak"), tuple("client_id", "THE_CLIENT_ID"));
        assertMetric("keycloak_registrations", 1, tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));
    }

    @Test
    public void shouldCorrectlyCountRefreshTokens() throws IOException {
        // with id provider defined
        final Event event1 = createEvent(EventType.REFRESH_TOKEN, DEFAULT_REALM_ID, "THE_CLIENT_ID", tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordRefreshToken(event1, realmProvider);
        assertMetric("keycloak_refresh_tokens", 1, tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));

        // without id provider defined
        final Event event2 = createEvent(EventType.REFRESH_TOKEN, DEFAULT_REALM_ID, "THE_CLIENT_ID");
        PrometheusExporter.instance().recordRefreshToken(event2, realmProvider);
        assertMetric("keycloak_refresh_tokens", 1, tuple("provider", "keycloak"), tuple("client_id", "THE_CLIENT_ID"));
        assertMetric("keycloak_refresh_tokens", 1, tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));
    }

    @Test
    public void shouldCorrectlyCountRefreshTokensErrors() throws IOException {
        // with id provider defined
        final Event event1 = createEvent(EventType.REFRESH_TOKEN_ERROR, DEFAULT_REALM_ID, "THE_CLIENT_ID", "user_not_found", tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordRefreshTokenError(event1, realmProvider);
        assertMetric("keycloak_refresh_tokens_errors", 1, tuple("provider", "THE_ID_PROVIDER"), tuple("error", "user_not_found"), tuple("client_id", "THE_CLIENT_ID"));

        // without id provider defined
        final Event event2 = createEvent(EventType.REFRESH_TOKEN_ERROR, DEFAULT_REALM_ID, "THE_CLIENT_ID", "user_not_found");
        PrometheusExporter.instance().recordRefreshTokenError(event2, realmProvider);
        assertMetric("keycloak_refresh_tokens_errors", 1, tuple("provider", "keycloak"), tuple("error", "user_not_found"), tuple("client_id", "THE_CLIENT_ID"));
        assertMetric("keycloak_refresh_tokens_errors", 1, tuple("provider", "THE_ID_PROVIDER"), tuple("error", "user_not_found"), tuple("client_id", "THE_CLIENT_ID"));
    }

    @Test
    public void shouldCorrectlyCountClientLogins() throws IOException {
        // with id provider defined
        final Event event1 = createEvent(EventType.CLIENT_LOGIN, DEFAULT_REALM_ID, "THE_CLIENT_ID", tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordClientLogin(event1, realmProvider);
        assertMetric("keycloak_client_logins", 1, tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));

        // without id provider defined
        final Event event2 = createEvent(EventType.CLIENT_LOGIN, DEFAULT_REALM_ID, "THE_CLIENT_ID");
        PrometheusExporter.instance().recordClientLogin(event2, realmProvider);
        assertMetric("keycloak_client_logins", 1, tuple("provider", "keycloak"), tuple("client_id", "THE_CLIENT_ID"));
        assertMetric("keycloak_client_logins", 1, tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));
    }

    @Test
    public void shouldCorrectlyCountClientLoginAttempts() throws IOException {
        // with id provider defined
        final Event event1 = createEvent(EventType.CLIENT_LOGIN_ERROR, DEFAULT_REALM_ID, "THE_CLIENT_ID", "user_not_found", tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordClientLoginError(event1, realmProvider);
        assertMetric("keycloak_failed_client_login_attempts", 1, tuple("provider", "THE_ID_PROVIDER"), tuple("error", "user_not_found"), tuple("client_id", "THE_CLIENT_ID"));

        // without id provider defined
        final Event event2 = createEvent(EventType.CLIENT_LOGIN_ERROR, DEFAULT_REALM_ID, "THE_CLIENT_ID", "user_not_found");
        PrometheusExporter.instance().recordClientLoginError(event2, realmProvider);
        assertMetric("keycloak_failed_client_login_attempts", 1, tuple("provider", "keycloak"), tuple("error", "user_not_found"), tuple("client_id", "THE_CLIENT_ID"));
        assertMetric("keycloak_failed_client_login_attempts", 1, tuple("provider", "THE_ID_PROVIDER"), tuple("error", "user_not_found"), tuple("client_id", "THE_CLIENT_ID"));
    }

    @Test
    public void shouldCorrectlyCountCodeToTokens() throws IOException {
        // with id provider defined
        final Event event1 = createEvent(EventType.CODE_TO_TOKEN, DEFAULT_REALM_ID, "THE_CLIENT_ID", tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordCodeToToken(event1, realmProvider);
        assertMetric("keycloak_code_to_tokens", 1, tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));

        // without id provider defined
        final Event event2 = createEvent(EventType.CODE_TO_TOKEN, DEFAULT_REALM_ID, "THE_CLIENT_ID");
        PrometheusExporter.instance().recordCodeToToken(event2, realmProvider);
        assertMetric("keycloak_code_to_tokens", 1, tuple("provider", "keycloak"), tuple("client_id", "THE_CLIENT_ID"));
        assertMetric("keycloak_code_to_tokens", 1, tuple("provider", "THE_ID_PROVIDER"), tuple("client_id", "THE_CLIENT_ID"));
    }

    @Test
    public void shouldCorrectlyCountCodeToTokensErrors() throws IOException {
        // with id provider defined
        final Event event1 = createEvent(EventType.CODE_TO_TOKEN_ERROR, DEFAULT_REALM_ID, "THE_CLIENT_ID", "user_not_found", tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordCodeToTokenError(event1, realmProvider);
        assertMetric("keycloak_code_to_tokens_errors", 1, tuple("provider", "THE_ID_PROVIDER"), tuple("error", "user_not_found"), tuple("client_id", "THE_CLIENT_ID"));

        // without id provider defined
        final Event event2 = createEvent(EventType.CODE_TO_TOKEN_ERROR, DEFAULT_REALM_ID, "THE_CLIENT_ID", "user_not_found");
        PrometheusExporter.instance().recordCodeToTokenError(event2, realmProvider);
        assertMetric("keycloak_code_to_tokens_errors", 1, tuple("provider", "keycloak"), tuple("error", "user_not_found"), tuple("client_id", "THE_CLIENT_ID"));
        assertMetric("keycloak_code_to_tokens_errors", 1, tuple("provider", "THE_ID_PROVIDER"), tuple("error", "user_not_found"), tuple("client_id", "THE_CLIENT_ID"));
    }

    @Test
    public void shouldCorrectlyRecordGenericEvents() throws IOException {
        final Event event1 = createEvent(EventType.UPDATE_EMAIL);
        PrometheusExporter.instance().recordGenericEvent(event1, realmProvider);
        assertMetric("keycloak_user_event_UPDATE_EMAIL", 1);
        PrometheusExporter.instance().recordGenericEvent(event1, realmProvider);
        assertMetric("keycloak_user_event_UPDATE_EMAIL", 2);


        final Event event2 = createEvent(EventType.REVOKE_GRANT);
        PrometheusExporter.instance().recordGenericEvent(event2, realmProvider);
        assertMetric("keycloak_user_event_REVOKE_GRANT", 1);
        assertMetric("keycloak_user_event_UPDATE_EMAIL", 2);
    }

    @Test
    public void shouldCorrectlyRecordGenericAdminEvents() throws IOException {
        final AdminEvent event1 = new AdminEvent();
        event1.setOperationType(OperationType.ACTION);
        event1.setResourceType(ResourceType.AUTHORIZATION_SCOPE);
        event1.setRealmId(DEFAULT_REALM_ID);
        PrometheusExporter.instance().recordGenericAdminEvent(event1, realmProvider);
        assertMetric("keycloak_admin_event_ACTION", 1, tuple("resource", "AUTHORIZATION_SCOPE"));
        PrometheusExporter.instance().recordGenericAdminEvent(event1, realmProvider);
        assertMetric("keycloak_admin_event_ACTION", 2, tuple("resource", "AUTHORIZATION_SCOPE"));


        final AdminEvent event2 = new AdminEvent();
        event2.setOperationType(OperationType.UPDATE);
        event2.setResourceType(ResourceType.CLIENT);
        event2.setRealmId(DEFAULT_REALM_ID);
        PrometheusExporter.instance().recordGenericAdminEvent(event2, realmProvider);
        assertMetric("keycloak_admin_event_UPDATE", 1, tuple("resource", "CLIENT"));
        assertMetric("keycloak_admin_event_ACTION", 2, tuple("resource", "AUTHORIZATION_SCOPE"));
    }

    @Test
    public void shouldCorrectlyRecordResponseDurations() throws IOException {
        environmentVariables.set("URI_METRICS_ENABLED", "true");
        PrometheusExporter.instance().recordRequestDuration(200, 5, "GET", "admin,admin/serverinfo", "auth/realm");
        assertGenericMetric("keycloak_request_duration_count", 1,
            tuple("code","200"), tuple("method", "GET"), tuple("resource", "admin,admin/serverinfo"), tuple("uri", "auth/realm"));
        assertGenericMetric("keycloak_request_duration_sum", 5,
            tuple("code","200"), tuple("method", "GET"), tuple("resource", "admin,admin/serverinfo"), tuple("uri", "auth/realm"));
    }

    @Test
    public void shouldCorrectlyRecordResponseTotal() throws IOException {
        environmentVariables.set("URI_METRICS_ENABLED", "true");
        PrometheusExporter.instance().recordResponseTotal(200, "GET", "admin,admin/serverinfo", "auth/realm");
        PrometheusExporter.instance().recordResponseTotal(500, "POST", "admin,admin/serverinfo", "auth/realm");
        assertGenericMetric("keycloak_response_total", 1,
            tuple("code", "200"), tuple("method", "GET"), tuple("resource", "admin,admin/serverinfo"), tuple("uri", "auth/realm"));
        assertGenericMetric("keycloak_response_total", 1,
            tuple("code", "500"), tuple("method", "POST"), tuple("resource", "admin,admin/serverinfo"), tuple("uri", "auth/realm"));
    }

    @Test
    public void shouldCorrectlyRecordResponseErrors() throws IOException {
        environmentVariables.set("URI_METRICS_ENABLED", "true");
        PrometheusExporter.instance().recordResponseError(500, "POST", "admin,admin/serverinfo", "auth/realm");
        assertGenericMetric("keycloak_response_errors_total", 1,
            tuple("code", "500"), tuple("method", "POST"), tuple("resource", "admin,admin/serverinfo"), tuple("uri", "auth/realm"));
    }

    @Test
    public void shouldTolerateNullLabels() throws IOException {
        final Event nullEvent = new Event();
        nullEvent.setClientId(null);
        nullEvent.setError(null);
        nullEvent.setRealmId(null);
        PrometheusExporter.instance().recordLoginError(nullEvent, realmProvider);
        assertMetric("keycloak_failed_login_attempts", 1, "", tuple("provider", "keycloak"), tuple("error", ""), tuple("client_id", ""));
    }

    @Test
    public void shouldBuildPushgateway() throws IOException {
        final String envVar = "PROMETHEUS_PUSHGATEWAY_ADDRESS";
        final String address = "localhost:9091";
        environmentVariables.set(envVar, address);
        Assert.assertNotNull(PrometheusExporter.instance().PUSH_GATEWAY);
    }

    @Test
    public void shouldBuildPushgatewayWithHttps() throws IOException {
        final String envVar = "PROMETHEUS_PUSHGATEWAY_ADDRESS";
        final String address = "https://localhost:9091";
        environmentVariables.set(envVar, address);
        Assert.assertNotNull(PrometheusExporter.instance().PUSH_GATEWAY);
    }

    @Test
    public void shouldNotBuildPushgateway() throws IOException {
        Assert.assertNull(PrometheusExporter.instance().PUSH_GATEWAY);
    }


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

    private void assertMetric(String metricName, double metricValue, String realm, Tuple<String, String>... labels) throws IOException {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            PrometheusExporter.instance().export(stream);
            String result = new String(stream.toByteArray());

            final StringBuilder builder = new StringBuilder();

            builder.append(metricName).append("_total").append("{");
            builder.append("realm").append("=\"").append(realm).append("\",");

            for (Tuple<String, String> label : labels) {
                builder.append(label.left).append("=\"").append(label.right).append("\",");
            }

            builder.append("} ").append(metricValue);

            MatcherAssert.assertThat(result, containsString(builder.toString()));
        }
    }

    private void assertMetric(String metricName, double metricValue, Tuple<String, String>... labels) throws IOException {
        this.assertMetric(metricName, metricValue, DEFAULT_REALM_NAME, labels);
    }

    private Event createEvent(EventType type, String realm, String clientId, String error, Tuple<String, String>... tuples) {
        final Event event = new Event();
        event.setType(type);
        event.setRealmId(realm);
        event.setClientId(clientId);
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
        return this.createEvent(type, DEFAULT_REALM_ID, "THE_CLIENT_ID", (String) null, tuples);
    }

    private Event createEvent(EventType type, String realm, String clientId, Tuple<String, String>... tuples) {
        return this.createEvent(type, realm, clientId, (String) null, tuples);
    }

    private Event createEvent(EventType type, String realm, Tuple<String, String>... tuples) {
        return this.createEvent(type, realm, "THE_CLIENT_ID", (String) null, tuples);
    }

    private Event createEvent(EventType type) {
        return createEvent(type, DEFAULT_REALM_ID, "THE_CLIENT_ID",(String) null);
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
