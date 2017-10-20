#!/bin/bash

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

export JAVA_APP="com.axios.ccdp.mesos.fmwk.CCDPEngineMain"
export BASE_NAME="CCDPEngineMain"
#export CCDP_JAR_NAME="mesos-ccdp-exec.jar"
export CCDP_SKIP_REDIRECTION=1

${CCDP_HOME}/bin/run_service.sh $@
