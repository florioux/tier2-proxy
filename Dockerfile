FROM amazoncorretto:24-alpine3.22-jdk

ENV CERTS_FOLDER=${CERTS_FOLDER:-/tmp/certs}
RUN adduser -S -u 1001 1001 && \
    mkdir -p "${CERTS_FOLDER}" && chown 1001 "${CERTS_FOLDER}"

COPY target/tier2-proxy-*.jar app.jar

RUN chown 1001 /app.jar
USER 1001
ENTRYPOINT ["java", "-jar", "/app.jar"]
