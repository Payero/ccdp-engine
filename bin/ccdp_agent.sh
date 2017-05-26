#!/bin/bash

export JAVA_APP="com.axios.ccdp.newgen.CcdpAgent"
export BASE_NAME="CcdpAgent"
export CCDP_JAR_NAME="ccdp-engine.jar"

my_dir="$(dirname "$0")"
${my_dir}/run_app.sh $@
