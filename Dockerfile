FROM openjdk:15-jdk-alpine

# Use Tini: https://github.com/krallin/tini
RUN apk add --no-cache tini
ENTRYPOINT ["/sbin/tini", "--"]

ARG APPLICATION_USER=www
RUN adduser -D -g '' $APPLICATION_USER

RUN mkdir /app
RUN chown -R $APPLICATION_USER /app

USER $APPLICATION_USER

# NOTE: this needs to also be copied verbatim to CMD, using the variable directly won't work: https://stackoverflow.com/q/52789177
ARG JAR_FILE="dojo-latest-server.jar"

COPY ./server/build/libs/$JAR_FILE /app/$JAR_FILE
WORKDIR /app

CMD ["java", "-server", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseContainerSupport", "-XX:+UseG1GC", "-XX:+UseStringDeduplication", "-jar", "dojo-latest-server.jar"]
