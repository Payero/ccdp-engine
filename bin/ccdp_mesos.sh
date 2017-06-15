#!/bin/bash

export JAVA_APP="com.axios.ccdp.mesos.fmwk.CCDPEngineMain"
export BASE_NAME="CCDPEngineMain"
export CCDP_JAR_NAME="mesos-ccdp-exec.jar"

my_dir="$(dirname "$0")"
${my_dir}/run_app.sh $@
