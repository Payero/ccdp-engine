#!/bin/bash

TEMP=`getopt -o hc:f:j:d:t: --longoptions help,config-file:,file:,jobs:dest:,kill-task: -n $0 -- "$@"`

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
  echo ''
  exit 0
}



if [ -z ${CCDP_HOME+x} ]; then 
  echo "CCDP_HOME is unset, trying default"; 
  if [ -d "/data/ccdp-engine" ]; then
    export CCDP_HOME=/data/ccdp-engine
  else
    echo "Could not find CCDP_HOME, exiting"
    exit -1
  fi

else 
  echo "CCDP_HOME is set to '$CCDP_HOME'"; 
fi

CFG_FILE=${CCDP_HOME}/config/ccdp-config.properties
TASK=""
APP_ARGS=""
JSON=""
DEST=""
KILL=""


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

unset _CLASSPATH
JAR_FILE=""
for i in $( find $CCDP_HOME -name ccdp-engine.jar ); do 
  echo "Found $i"
  JAR_FILE=$i
  _CLASSPATH=${JAR_FILE}
  break
done

if [ -z "$JAR_FILE" ]; then
  CCDP_LIB_DIR=${CCDP_HOME}/lib
  CCDP_CLS_DIR=${CCDP_HOME}/classes

	echo "The ccdp-engine.jar was not found, using all jars"
	unset _JARS
  _JARS=$(find "$CCDP_LIB_DIR" -follow -name "*.jar" -xtype f 2>/dev/null | sort | tr '\n' ':')

  _CLASSPATH=${_JARS}:${CCDP_CLS_DIR}
fi

CMD="java -cp ${_CLASSPATH} com.axios.ccdp.test.CcdpMsgSender $APP_ARGS"

echo "Running: ${CMD} "
exec $CMD 
