# KeyCloak Metrics SPI

A [Service Provider](http://www.keycloak.org/docs/3.0/server_development/topics/providers.html) that adds a metrics endpoint to KeyCloak. The endpoint returns metrics data ready to be scraped by [Prometheus](https://prometheus.io/).

Two distinct providers are defined:

* MetricsEventListener to record the internal KeyCloak events
* MetricsEndpoint to expose the data through a custom endpoint

The endpoint lives under `<url>/auth/realms/<realm>/metrics`. It will return data for all realms, no matter which realm
you use in the URL (you can just default to `/auth/realms/master/metrics`).

## Running the tests

```sh
$ ./gradlew test
```

## Build

The project is packages as a jar file and bundles the prometheus client libraries.

```sh
$ ./gradlew jar
```

builds the jar and writes it to _build/libs_.

## Usage

Just drop the jar into the _providers_ subdirectory of your KeyCloak installation. To enbale the event listener go to _Manage -> Events -> Config_. The _Event Listeners_ configuration should have an entry named `metrics-listener`.

## Metrics

The endpoint will return JVM performance metrics, counters of all KeyCloak events and the number of logged in users (_kc_logged_in_users_).