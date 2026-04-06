FROM eclipse-temurin:17-jdk-focal as build

RUN rm -f /etc/apt/apt.conf.d/docker-clean && \
    apt-get update && \
    apt-get install -y git curl nano && \
    apt-get clean

WORKDIR /usr/src/miningframework

COPY . .

RUN ./gradlew installDist

FROM eclipse-temurin:17-jre-focal

RUN rm -f /etc/apt/apt.conf.d/docker-clean && \
    apt-get update && \
    apt-get install -y git procps && \
    apt-get clean

WORKDIR /usr/src/miningframework

COPY --from=build /usr/src/miningframework/build/install/miningframework /usr/local/framework
COPY --from=build /usr/src/miningframework/dependencies /usr/local/framework/dependencies

RUN chmod +x /usr/local/framework/bin/miningframework
RUN chmod -R 775 /usr/local/framework/dependencies/

ENV PATH="/usr/local/framework/bin:${PATH}"

ENTRYPOINT ["miningframework"]
