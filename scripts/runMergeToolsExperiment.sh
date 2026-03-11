#!/bin/bash

cd "$(dirname "$0")/.."

if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
else
  echo "Erro: Arquivo .env não encontrado na raiz do projeto!"
  exit 1
fi

echo "Starting Java Mining..."
docker-compose run --rm mining_worker -i injectors.GenericMergeModuleJava -t 4 -m 1 -e .java -a "${GITHUB_TOKEN}" input/mergeTools/filtered_repos/java.csv mergeAnalysisOutput/java

echo "Starting Rust Mining..."
docker-compose run --rm mining_worker -i injectors.GenericMergeModule -t 4 -m 1 -e .rs -a "${GITHUB_TOKEN}" input/mergeTools/filtered_repos/rs.csv mergeAnalysisOutput/rust

echo "Starting Python Mining..."
docker-compose run --rm mining_worker -i injectors.GenericMergeModule -t 4 -m 1 -e .py -a "${GITHUB_TOKEN}" input/mergeTools/filtered_repos/py.csv mergeAnalysisOutput/python

echo "Starting Javascript Mining..."
docker-compose run --rm mining_worker -i injectors.GenericMergeModule -t 4 -m 1 -e .js -a "${GITHUB_TOKEN}" input/mergeTools/filtered_repos/js.csv mergeAnalysisOutput/js

echo "Starting Go Mining..."
docker-compose run --rm mining_worker -i injectors.GenericMergeModule -t 4 -m 1 -e .go -a "${GITHUB_TOKEN}" input/mergeTools/filtered_repos/go.csv mergeAnalysisOutput/go

echo "All experiments finished!"