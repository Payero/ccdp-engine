#!/bin/bash

export JAVA_APP="com.axios.ccdp.newgen.CcdpAgent"

my_dir="$(dirname "$0")"
${my_dir}/run_app.sh $@
