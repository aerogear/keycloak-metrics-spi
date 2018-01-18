# KeyCloak Metrics SPI

A [Service Provider](http://www.keycloak.org/docs/3.0/server_development/topics/providers.html) that adds a metrics endpoint to KeyCloak. The endpoint returns metrics data ready to be scraped by [Prometheus](https://prometheus.io/).

Two distinct providers are defined:

* MetricsEventListener to record the internal KeyCloak events
* MetricsEndpoint to expose the data through a custom endpoint

The endpoint lives under `<url>/auth/realms/<realm>/metrics`. It will return data for all realms, no matter which realm
you use in the URL (you can just default to `/auth/realms/master/metrics`).

## Build

The project is packages as a jar file and bundles the prometheus client libraries.

```sh
$ gradle jar
```

builds the jar and writes it to _build/libs_.

## Usage

To use the metrics, make sure that the event listener is added to your Realm. Go to _Manage -> Events -> Config_. The _Event Listeners_ configuration should have an entry named `metrics-listener`.
