#!/bin/bash

export JAVA_APP="com.axios.ccdp.newgen.CcdpMainApplication"
export BASE_NAME="CcdpMainApplication"
export CCDP_JAR_NAME="ccdp-engine.jar"

my_dir="$(dirname "$0")"
${my_dir}/run_app.sh $@
