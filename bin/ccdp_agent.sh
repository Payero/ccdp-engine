#!/bin/bash

export JAVA_APP="com.axios.ccdp.newgen.CcdpAgent"
export BASE_NAME="CcdpAgent"

my_dir="$(dirname "$0")"
${my_dir}/run_app.sh $@