#!/bin/bash

# Garante que estamos na raiz do projeto
cd "$(dirname "$0")/.."

# Carrega o token do GitHub
if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
else
  echo "Error: .env file not found!"
  exit 1
fi

echo "Mounting docker image"
docker build -t phls2_mining-mergetools:latest .

DOCKER_ARGS="--rm --cpuset-cpus=0-15 \
  -v $(pwd)/input:/usr/src/miningframework/input \
  -v $(pwd)/dependencies:/usr/src/miningframework/dependencies \
  -v $(pwd)/mergeAnalysisOutput:/usr/src/miningframework/mergeAnalysisOutput \
  -v $(pwd)/clonedRepositories:/usr/src/miningframework/clonedRepositories \
  -v $(pwd)/output:/usr/src/miningframework/output \
  phls2_mining-mergetools:latest"

echo "Starting Rust Mining..."
docker run $DOCKER_ARGS -i injectors.GenericMergeModule -t 16 -k -e .rs -a "${GITHUB_TOKEN}" input/mergeTools/filtered_repos/rs.csv mergeAnalysisOutput/rust

echo "Starting Javascript Mining..."
docker run $DOCKER_ARGS -i injectors.GenericMergeModule -t 16 -k -e .js -a "${GITHUB_TOKEN}" input/mergeTools/filtered_repos/js.csv mergeAnalysisOutput/js

echo "Starting Go Mining..."
docker run $DOCKER_ARGS -i injectors.GenericMergeModule -t 16 -k -e .go -a "${GITHUB_TOKEN}" input/mergeTools/filtered_repos/go.csv mergeAnalysisOutput/go

echo "Starting Python Mining..."
docker run $DOCKER_ARGS -i injectors.GenericMergeModule -t 16 -k -e .py -a "${GITHUB_TOKEN}" input/mergeTools/filtered_repos/py.csv mergeAnalysisOutput/python

echo "Starting Java Mining..."
docker run $DOCKER_ARGS -i injectors.GenericMergeModuleJava -t 16 -k -e .java -a "${GITHUB_TOKEN}" input/mergeTools/filtered_repos/java.csv mergeAnalysisOutput/java

echo "All experiments finished!"