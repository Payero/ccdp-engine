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


#############################################################################
# Need to find where CCDP is installed, if not set check default location   #
# unfortunately this needs to be on each script because we are not sure     #
# where CCDP_HOME is yet                                                    #
#############################################################################
set_home()
{
  if [ -z "$CCDP_HOME" ]; then
    
    if [ -d /data/ccdp/ccdp-engine ] ; then
      CCDP_HOME=/data/ccdp/ccdp-engine
    else
      # Is not in the default location, resolving links (just in case)
      PRG="$0"
      progname=`basename "$0"`
      saveddir=`pwd`

      # need this for relative symlinks
      dirname_prg=`dirname "$PRG"`
      cd $dirname_prg

      while [ -h "$PRG" ]; do
        ls=`ls -ld "$PRG"`
        link=`expr "$ls" : '.*-> \(.*\)$'`
        if expr "$link" : '.*/.*' > /dev/null; then
          PRG="$link"
        else
          PRG=`dirname "$PRG"`"/$link"
        fi
      done

      CCDP_HOME=`dirname "$PRG"`/..

      cd $saveddir

      #make it fully qualified
      CCDP_HOME=`cd "$CCDP_HOME" && pwd`
    fi
  fi

  if [ -d "$CCDP_HOME" ]; then
    export CCDP_HOME
  else
    echo ""
    echo "ERROR:  Could not determine CCDP_HOME location, exiting"
    echo ""
    exit -2
  fi
}
set_home

## ------------- Now the actual parameters to run the service -------------- ##
export JAVA_APP="com.axios.ccdp.fmwk.CcdpAgent"
export BASE_NAME="CcdpAgent"
export CCDP_JAR_NAME="ccdp-engine.jar"
export CCDP_SKIP_REDIRECTION=1
export CCDP_NODE_TYPE=NIFI

${CCDP_HOME}/bin/run_service.sh $@
