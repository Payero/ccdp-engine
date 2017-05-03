#!/bin/bash

export JAVA_APP="com.axios.ccdp.newgen.CcdpMainApplication"

my_dir="$(dirname "$0")"
${my_dir}/run_app.sh $@
