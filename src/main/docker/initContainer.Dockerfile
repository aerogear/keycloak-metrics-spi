FROM busybox

ARG VERSION

COPY build/libs/keycloak-metrics-spi-${VERSION}.jar /keycloak-metrics-spi/
