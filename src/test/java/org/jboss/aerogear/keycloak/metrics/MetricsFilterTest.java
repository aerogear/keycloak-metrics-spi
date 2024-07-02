package org.jboss.aerogear.keycloak.metrics;

import io.prometheus.client.CollectorRegistry;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.org.webcompere.systemstubs.rules.EnvironmentVariablesRule;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MetricsFilterTest {

  private MetricsFilter metricsFilter;

  @Rule
  public final EnvironmentVariablesRule environmentVariables = new EnvironmentVariablesRule();

  @Before
  public void resetSingleton() throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
    environmentVariables.set("URI_METRICS_ENABLED", "true");
    metricsFilter = MetricsFilter.instance();

    Field instance = PrometheusExporter.class.getDeclaredField("INSTANCE");
    instance.setAccessible(true);
    instance.set(null, null);
    CollectorRegistry.defaultRegistry.clear();
  }

  @Test
  public void testHttpMetricForNotFoundUri() throws IOException {
    var req = mockRequest("GET", List.of("auth", "realms", "not_existing_realm", "openid-connect", "token"));

    var resp = mock(ContainerResponseContext.class);
    when(resp.getStatus()).thenReturn(404);

    metricsFilter.filter(req, resp);

    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    PrometheusExporter.instance().export(stream);
    assertThat(stream.toString(), containsString("keycloak_response_created{code=\"404\",method=\"GET\",resource=\"token,openid-connect\",uri=\"NOT_FOUND\""));
  }

  @Test
  public void testHttpMetricForRedirectUri() throws IOException {
    var req = mockRequest("GET", List.of("auth", "realms", "my_realm", "openid-connect", "login"));

    var resp = mock(ContainerResponseContext.class);
    when(resp.getStatus()).thenReturn(302);

    metricsFilter.filter(req, resp);

    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    PrometheusExporter.instance().export(stream);
    assertThat(stream.toString(), containsString("keycloak_response_created{code=\"302\",method=\"GET\",resource=\"login,openid-connect\",uri=\"REDIRECTION\""));
  }

  private static ContainerRequestContext mockRequest(String method, List<String> matchedUri) {
    var req = mock(ContainerRequestContext.class);
    when(req.getMethod()).thenReturn(method);
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getMatchedURIs()).thenReturn(matchedUri);
    when(req.getUriInfo()).thenReturn(uriInfo);
    return req;
  }
}
