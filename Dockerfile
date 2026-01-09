FROM ubuntu:24.04 as build

RUN apt-get update && \
    apt-get install -y openjdk-17-jdk git curl nano && \
    apt-get clean;

WORKDIR /usr/src/miningframework

COPY . .

RUN ./gradlew installDist

FROM ubuntu:24.04

RUN apt-get update && \
    apt-get install -y openjdk-17-jre git procps && \
    apt-get clean;

WORKDIR /usr/src/miningframework

COPY --from=build /usr/src/miningframework/build/install/miningframework /usr/local/framework
COPY --from=build /usr/src/miningframework/dependencies /usr/local/framework/dependencies

RUN chmod +x /usr/local/framework/bin/miningframework
RUN chmod -R 775 /usr/local/framework/dependencies/

ENV PATH="/usr/local/framework/bin:${PATH}"

ENTRYPOINT ["miningframework"]
