package org.jboss.aerogear.keycloak.metrics;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

public class MetricsEndpointFactory implements RealmResourceProviderFactory {
  @Override
  public RealmResourceProvider create(KeycloakSession session) {
    return new MetricsEndpoint();
  }

  @Override
  public void init(Config.Scope config) {
    // nothing to do
  }

  @Override
  public void postInit(KeycloakSessionFactory factory) {
    // nothing to do
  }

  @Override
  public void close() {
    // nothing to close
  }

  @Override
  public String getId() {
    return MetricsEndpoint.ID;
  }
}
