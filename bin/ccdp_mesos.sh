#!/bin/bash

export JAVA_APP="com.axios.ccdp.mesos.fmwk.CCDPEngineMain"
export BASE_NAME="CCDPEngineMain"
export CCDP_JAR_NAME="mesos-ccdp-exec.jar"

echo "The Jar $CCDP_JAR_NAME"
my_dir="$(dirname "$0")"
${my_dir}/run_app.sh $@
