#!/bin/bash

export JAVA_APP="com.axios.ccdp.newgen.CcdpMainApplication"
export BASE_NAME="CcdpMainApplication"

my_dir="$(dirname "$0")"
${my_dir}/run_app.sh $@
