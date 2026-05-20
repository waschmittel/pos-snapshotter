#!/usr/bin/env bash

# set -x # set this for debugging to print all called commands
set -e # fail non non-zero exit status
set -u # fail on unset variable
set -o pipefail # fail if a pipe fails

mvn clean package

cd target

# Determine version from pom.xml if not provided
VERSION=${1:-"1.0.0"}

jpackage \
    --name "PosSnapshotter" \
    --input . \
    --main-jar possnapshotter.jar \
    --description 'Tool to take snapshots and print them to an ESC/POS printer.' \
    --vendor 'Flubba' \
    --app-version "${VERSION}"
