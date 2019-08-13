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

# Prints the Usage
usage()
{
  echo ''
  echo 'Runs the class used for sending update messages to the engine.  It'
  echo 'uses the ${CCDP_HOME}/config/ccdp-config.json unless another '
  echo 'one is provided using the -c argument'
  echo ''
  echo 'usage: $0'
  echo '   -h,--help                Shows this message'
  echo '   -c, --config-file <arg>  Path to the configuration file.'
  echo '   -f  --filename    The name of the resource file to use'
  echo ''
  exit 0
}


CFG_FILE=${CCDP_HOME}/config/ccdp-config.json
APP_ARGS=""
JAVA_OPTS=""
JAVA_APP="com.axios.ccdp.test.CcdpStatusSender"

# Getting all the arguments
SZ=$#
INDX=0

_ARGS=""
while [ $INDX -lt $SZ ] ; do
  case "$1" in
  -h | --help ) usage ; break ;;
  -c | --config-file ) CFG_FILE=$2 ; shift 2 ;;
  * ) _ARGS+="$1 "; shift 1 ;;
  esac
  INDX=$[$INDX+1]
done

if [ -z "$CFG_FILE" ] ; then
  echo "The Configuration file was not provided"
  usage
fi

APP_ARGS="-c ${CFG_FILE} ${_ARGS}"
JAVA_OPTS="-Dccdp.config.file=${CFG_FILE}"

unset _CLASSPATH

# if we want to use a specific jar file, find it
if [ -z $CCDP_JAR_NAME ]; then
  CCDP_LIB_DIR=${CCDP_HOME}/lib
  CCDP_CLS_DIR=${CCDP_HOME}/classes

  unset _JARS
  _JARS=$(find "$CCDP_LIB_DIR" -follow -name "*.jar" -xtype f 2>/dev/null | sort | tr '\n' ':')

  _CLASSPATH=${_JARS}:${CCDP_CLS_DIR}

# We are not using jar file, we are using the classes and lib directories
else
  JAR_FILE=""
  for i in $( find $CCDP_HOME -name "${CCDP_JAR_NAME}" ); do 
    JAR_FILE=$i
    _CLASSPATH=${JAR_FILE}
    break
  done

fi


if [ -z ${JAVA_HOME} ]; then 
  JAVA_CMD=java
else
  JAVA_CMD=${JAVA_HOME}/bin/java
fi

CMD="${JAVA_CMD} -cp ${_CLASSPATH} ${JAVA_OPTS} ${JAVA_APP} $APP_ARGS"

echo "Running: ${CMD} "
exec $CMD 


