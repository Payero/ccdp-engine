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

TEMP=`getopt -o hc:f:j:d:t:r: --longoptions help,config-file:,file:,jobs:dest:,kill-task:,reply-to: -n $0 -- "$@"`

# Prints the Usage
usage()
{
  echo ''
  echo 'Sends a taks to the framework.  If the configuration file is not'
  echo 'it uses the ${CCDP_HOME}/config/ccdp-config.properties'
  echo ''
  echo 'usage: class com.axios.ccdp.mesos.test.CcdpTaskSender'
  echo '   -c, --config-file <arg>   Path to the configuration file.'
  echo '   -f,--file <arg>          Optional JSON file with the jobs to run'
  echo '   -h,--help                Shows this message'
  echo '   -j,--jobs <arg>          Optional JSON file with the jobs to run'
  echo '                            passed as a string'
  echo '   -d,--dest <arg>          The name of the Queue to send the job'
  echo '   -t,--task <arg>          The name of task to kill'
  echo '   -r,--reply-to <arg>      The name of the channel to receive messages'
  echo ''
  exit 0
}


CFG_FILE=${CCDP_HOME}/config/ccdp-config.properties
TASK=""
APP_ARGS=""
JSON=""
DEST=""
KILL=""
REPLY=""


#CCDP_JAR_NAME="ccdp-engine.jar"
JAVA_APP="com.axios.ccdp.test.CcdpMsgSender"


eval set -- "$TEMP"
while true ; do
  case "$1" in
	-h | --help ) usage ; break ;;
	-c | --config-file ) CFG_FILE=$2 ; shift 2 ;;
	-f | --file ) TASK=$2 ; shift 2 ;;
	-c | --config-file ) CFG_FILE=$2 ; shift 2 ;;
	-j | --jobs ) JSON=$2 ; shift 2 ;;
	-d | --dest ) DEST=$2 ; shift 2 ;;
  -t | --task ) KILL=$2 ; shift 2 ;;
  -r | --reply-to ) REPLY=$2 ; shift 2 ;;
    * ) break ;;
  esac
done

if [ -z "$CFG_FILE" ] ; then
	echo "The Configuration file was not provided"
	usage
fi

APP_ARGS="-c ${CFG_FILE} "

# Was the destination specified?
if [ ! -z "$DEST" ] ; then
	APP_ARGS+=" -d $DEST"
fi


# If the task file was not passed
if [ -z "$TASK" -a -z "$KILL" ] ; then
	if [ -z "$JSON" ] ; then
		echo "ERROR: Either a file or a JSON needs to be provided"
		usage
	else
		APP_ARGS+=" -j $JSON"
	fi
else
  if [ ! -z "$TASK" ]; then 
	 APP_ARGS+=" -f $TASK"
  fi
fi

if [ ! -z "$KILL" ] ; then
  echo "Adding Task to kill"
  APP_ARGS+=" -t $KILL"
fi

if [ ! -z "$REPLY" ] ; then
  echo "Adding Channel to wait for "
  APP_ARGS+=" -r $REPLY"
fi

unset _CLASSPATH
JAR_FILE=""
for i in $( find $CCDP_HOME -name "${CCDP_JAR_NAME}" ); do 
  echo "Found $i"
  JAR_FILE=$i
  _CLASSPATH=${JAR_FILE}
  break
done

if [ -z "$JAR_FILE" ]; then
  CCDP_LIB_DIR=${CCDP_HOME}/lib
  CCDP_CLS_DIR=${CCDP_HOME}/classes

	echo "The ${CCDP_JAR_NAME} was not found, using all jars"
	unset _JARS
  _JARS=$(find "$CCDP_LIB_DIR" -follow -name "*.jar" -xtype f 2>/dev/null | sort | tr '\n' ':')

  _CLASSPATH=${_JARS}:${CCDP_CLS_DIR}
fi

if [ -z ${JAVA_HOME} ]; then 
  JAVA_CMD=java
else
  JAVA_CMD=${JAVA_HOME}/bin/java
fi

CMD="${JAVA_CMD} -cp ${_CLASSPATH} ${JAVA_APP} $APP_ARGS"

echo "Running: ${CMD} "
exec $CMD 
