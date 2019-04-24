#! /usr/bin/env bash

git describe --tags --abbrev=0 | sed 's/^v//'
