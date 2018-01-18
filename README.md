# KeyCloak Metrics SPI

A [SPI](http://www.keycloak.org/docs/3.0/server_development/topics/providers.html) that adds a metrics endpoint to KeyCloak.
The endpoint returns metrics data ready to be scraped by [Prometheus](https://prometheus.io/).

Two distinct SPIs are defined:

* MetricsEventListener to record the internal KeyCloak events
* MetricsEndpoint to expose the data through a custom endpoint

The endpoint lives under `<url>/auth/realms/<realm>/metrics`. It will return data for all realms, no matter which realm
you use in the URL (you can just default to `/auth/realms/master/metrics`).

To use the metrics, make sure that the event listener is added to your Realm. Go to _Manage -> Events -> Config_. The _Event Listeners_ configuration should have an entry named `metrics-listener`.
