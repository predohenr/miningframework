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

DOCKER_ARGS="--rm  \
  -v $(pwd)/input:/usr/src/miningframework/input \
  -v $(pwd)/dependencies:/usr/src/miningframework/dependencies \
  -v $(pwd)/mergeAnalysisOutput:/usr/src/miningframework/mergeAnalysisOutput \
  -v $(pwd)/clonedRepositories:/usr/src/miningframework/clonedRepositories \
  -v $(pwd)/output:/usr/src/miningframework/output \
  phls2_mining-mergetools:latest"

echo "Starting Java Mining..."
docker run $DOCKER_ARGS -i injectors.GenericMergeModuleJava -t 1 -m 1 -e .java -r 42 -a "${GITHUB_TOKEN}" input/mergeTools/test/test_java.csv mergeAnalysisOutput/java

echo "Starting Rust Mining..."
docker run $DOCKER_ARGS -i injectors.GenericMergeModule -t 1 -m 1 -e .rs -r 42 -a "${GITHUB_TOKEN}" input/mergeTools/test/test_rs.csv mergeAnalysisOutput/rust

echo "Starting Python Mining..."
docker run $DOCKER_ARGS -i injectors.GenericMergeModule -t 1 -m 1 -e .py -r 42 -a "${GITHUB_TOKEN}" input/mergeTools/test/test_py.csv mergeAnalysisOutput/python

echo "Starting Javascript Mining..."
docker run $DOCKER_ARGS -i injectors.GenericMergeModule -t 1 -m 1 -e .js -r 42 -a "${GITHUB_TOKEN}" input/mergeTools/test/test_js.csv mergeAnalysisOutput/js

echo "Starting Go Mining..."
docker run $DOCKER_ARGS -i injectors.GenericMergeModule -t 1 -m 1 -e .go -r 42 -a "${GITHUB_TOKEN}" input/mergeTools/test/test_go.csv mergeAnalysisOutput/go

echo "All experiments finished!"