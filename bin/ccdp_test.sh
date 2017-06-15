#!/bin/bash

export JAVA_APP="com.axios.ccdp.test.CCDPTest"
export BASE_NAME="CCDPTest"
export CCDP_SKIP_REDIRECTION=1

my_dir="$(dirname "$0")"
${my_dir}/run_app.sh $@
