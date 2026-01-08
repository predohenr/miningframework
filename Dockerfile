FROM ubuntu:22.04 as build

RUN apt-get update && \
    apt-get install -y openjdk-17-jdk git curl nano && \
    apt-get clean;

WORKDIR /usr/src/miningframework

COPY . .

RUN ./gradlew installDist

FROM ubuntu:22.04

RUN apt-get update && \
    apt-get install -y openjdk-17-jre git procps && \
    apt-get clean;

WORKDIR /usr/src/miningframework

COPY --from=build /usr/src/miningframework/build/install/miningframework /usr/local/framework
COPY --from=build /usr/src/miningframework/dependencies /usr/local/framework/dependencies

RUN useradd -ms /bin/bash miner
RUN chown -R miner:miner /usr/local/framework

RUN chmod +x /usr/local/framework/bin/miningframework
RUN chmod -R 775 /usr/local/framework/dependencies/

ENV PATH="/usr/local/framework/bin:${PATH}"

USER miner

ENTRYPOINT ["miningframework"]
