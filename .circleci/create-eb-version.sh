#! /usr/bin/env bash

set -eou pipefail

DOCKER_IMAGE="$1"

cat .circleci/Dockerrun.aws.json.template | sed "s/{{image}}/$DOCKER_IMAGE/" > .circleci/Dockerrun.aws.json
