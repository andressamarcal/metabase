#!/bin/sh

docker build -t mb-build-uberjar --target builder .
container_hash=`docker create mb-build-uberjar:latest`
docker cp "$container_hash":/app/source/target/uberjar/metabase.jar target/uberjar/metabase.jar
