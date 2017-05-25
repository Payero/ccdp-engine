#!/bin/bash

TEMP=`getopt -o hc:f:j:d: --longoptions help,config-file:,file:,jobs:dest: -n $0 -- "$@"`

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


eval set -- "$TEMP"
while true ; do
  case "$1" in
	-h | --help ) usage ; break ;;
	-c | --config-file ) CFG_FILE=$2 ; shift 2 ;;
	-f | --file ) TASK=$2 ; shift 2 ;;
	-c | --config-file ) CFG_FILE=$2 ; shift 2 ;;
	-j | --jobs ) JSON=$2 ; shift 2 ;;
	-d | --dest ) DEST=$2 ; shift 2 ;;
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
if [ -z "$TASK" ] ; then
	if [ -z "$JSON" ] ; then
		echo "ERROR: Either a file or a JSON needs to be provided"
		usage
	else
		APP_ARGS+=" -j $JSON"
	fi
else
	APP_ARGS+=" -f $TASK"
fi


JAR_FILE=""
for i in $( find $CCDP_HOME -name mesos-engine.jar ); do 
  echo "Found $i"
  JAR_FILE=$i
  break
done

if [ -z "$JAR_FILE" ]; then
	echo "The mesos-engine.jar was not found, exiting"
	exit 1
fi

CMD="java -cp ${JAR_FILE} com.axios.ccdp.test.CcdpTaskSender $APP_ARGS"

echo "Running: ${CMD} "
exec $CMD 
