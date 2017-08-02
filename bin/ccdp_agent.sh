#!/bin/bash
### BEGIN INIT INFO
# Provides:          ccdp-agent
# Required-Start:    $remote_fs
# Required-Stop:     $remote_fs
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: Cloud Computing Data Processing Agent
# Description:       Waits for commands to be executed on this VM
### END INIT INFO

export JAVA_APP="com.axios.ccdp.newgen.CcdpAgent"
export BASE_NAME="CcdpAgent"
export CCDP_JAR_NAME="ccdp-engine.jar"
#export CCDP_SKIP_REDIRECTION=1
echo "Running from $CCDP_HOME"

if [ -z "$CCDP_HOME" ]; then
  /data/ccdp/ccdp-engine/bin/run_app.sh $@
else
  ${CCDP_HOME}/bin/run_app.sh $@
fi
