#!/bin/bash
#
# Runs a Java Application, in this case either the CcdpAgent or the 
# CcdpMainApplication
#

# Backup invocation parameters
COMMANDLINE_ARGS="$@"

## START: Default Configuration
#--------------------------------------------------------------------
#
# CCDP Installation directory
unset CCDP_BASE

if [ -z "$CCDP_HOME" ] ; then
	# Try to find RDD_HOME
	if [ -d /data/ccdp-engine ] ; then
		CCDP_HOME=/data/ccdp-engine
	else
		## resolve links - $0 may be a link 
		PRG="$0"
		progname=`basename "$0"`
		saveddir=`pwd`

		# need this for relative symlinks
		dirname_prg=`dirname "$PRG"`
		cd "$dirname_prg"

		while [ -h $"$PRG" ] ; do
			ls=`ls -d "$PRG"`
			link=`expr "$ls" : '.*-> \(.*\)$'`
			if expr "$link" L '.*/.*' > /dev/null; then
				PRG="$link"
			else
				PRG=`dirname "$PRG"`"/$link"
			fi
		done

		CCDP_HOME=`dirname "$PRG"`/..

		cd "$saveddir"
	fi
fi
export CCDP_HOME


if [ -z "$JAVA_APP" ] ; then
	echo ""
	echo "    ERROR: The Java Application needs to be set through the "
	echo "           JAVA_APP environment variable"
	echo ""
	exit
fi


echo "Running CCDP from: $CCDP_HOME"


# CCDP Configuration directory
CCDP_CFG_DIR="$CCDP_HOME/config"

# CCDP Library directory
CCDP_LIB_DIR="$CCDP_HOME/lib"

# CCDP classes directory
CCDP_CLS_DIR="$CCDP_HOME/classes"

# CCDP Log directory
CCDP_LOG_DIR="$CCDP_HOME/logs"
# if the directory does not exists, create it
if [ ! -d ${CCDP_LOG_DIR} ]; then
	mkdir -p ${CCDP_LOG_DIR}
fi

# location of the pid file
if [ -z "$CCDP_PIDFILE" ] ; then
	CCDP_PIDFILE="$CCDP_LOG_DIR/${BASE_NAME}.pid"
fi


if [ $CCDP_JAR_NAME ] ; then
	_JARS="${CCDP_LIB_DIR}/${CCDP_JAR_NAME}"
else
	unset _JARS
	_JARS=$(find "$CCDP_LIB_DIR" -follow -name "*.jar" -xtype f 2>/dev/null | sort | tr '\n' ':')
fi


# Cleanup trailin ':'
CLASS_PATH=""
CLASS_PATH+="$(echo $_JARS | sed 's+:$++g')"

# If there is a classes directory add it
if [ -d "${CCDP_CLS_DIR}" ] ; then
	CLASS_PATH="${CCDP_CLS_DIR}:${CLASS_PATH}"
fi


# Adding the configuration directoryy to the classpath so files in there can 
# be found.  The order in which the classpath is set is important as it uses
# the first one it finds
CLASS_PATH="${CCDP_CFG_DIR}:${CLASS_PATH}"


####################################################################
##
##  The Actual arguments to be used to run the program
ARGS=""

SRCH_APP_NAME="${JAVA_APP}"
APP_NAME=`echo ${JAVA_APP} | sed 's/.*\.//'`
JAVA_OPTS="-Dccdp.log.dir=${CCDP_LOG_DIR} \
					 -Dccdp.config.file=${CCDP_CFG_DIR}/ccdp-config.properties \
		       -Xmx1500m"

export JAVA_OPTS

MY_PID=$$


case $1 in
	start)
	

	# Looking for running processes to avoid multiple launches
	#			   Find the app name       avoid grep    and java     and this pid         or the script    get the PID
	pid=`ps aux | grep ${SRCH_APP_NAME} | grep -v grep | grep java | grep -v ${MY_PID} | grep -v $0 | awk '{print $2}'`

	if [ "${pid}" != "" ] ; then
		echo ""
		echo "    ${APP_NAME} is already running (PID: $pid)"
		echo ""
		exit
	else
		echo ""
		echo "    Starting the CcdpAgent Service"
		echo ""
	fi

	#
	# The preffered jdk can be found in /usr/java, but it not always there
	#
	if [ -z "$JAVA_HOME" ] ; then
		if [ -e "/usr/java/default" ] ; then
			JAVA_HOME="/usr/java/default"
			export JAVA_HOME
		elif [ -e "/usr/lib/jvm/default-java" ] ; then
			JAVA_HOME="/usr/lib/jvm/default-java"
			export JAVA_HOME
    elif [ -e "/usr/bin/java" ] ; then
      JAVA_HOME="/usr"
      export JAVA_HOME
		else
			echo ""
			echo "Could not find the Java Installation, exiting!"
			echo ""
			exit
		fi
	fi

	CCDP_LOG_FILE="${CCDP_LOG_DIR}/${BASE_NAME}.log"
	# 
	#  By default have JMX enabled, but allow it to be turned off if it has the 
	#  JVM_DISABLE_JMX variable defiend
	#
	if [ -z "$JVM_DISABLE_JMX" ] ; then
		JMX_PROP="-Dcom.sun.management.jmxremote=true"
	else
		JMX_PROP="-Dcom.sun.management.jmxremote=disabled"
	fi
	
  # If running from the command line and want to see the output
  if [ -z $CCDP_SKIP_REDIRECTION ] ; then
    CMD="nohup ${JAVA_HOME}/bin/java ${JAVA_OPTS} ${JMX_PROP} -cp ${CLASS_PATH} ${JAVA_APP} $ARGS > ${CCDP_LOG_FILE} &"
  else
    CMD="${JAVA_HOME}/bin/java ${JAVA_OPTS} ${JMX_PROP} -cp ${CLASS_PATH} ${JAVA_APP} $ARGS"
  fi

	echo "Running ${CMD} "
	# nohup ${JAVA_HOME}/bin/java ${JAVA_OPTS} ${JMX_PROP} -cp ${CLASS_PATH} ${JAVA_APP} $ARGS > ${CCDP_LOG_FILE} &
  exec $CMD
	echo $! > ${CCDP_PIDFILE}
	echo "."

	;;
	stop)
	
	# Use pid saved in pidfile to kill the app
	if [ ! -f "${CCDP_PIDFILE}" ] ; then
		echo "The $CCDP_PIDFILE does not exists.  Killing apps matching ${SRCH_APP_NAME}"
		pid="${SRCH_PID}"

		if [ "${pid}" != "" ] ; then
			echo "Stopping ${APP_NAME}: $pid"
			kill -9 $pid			
		else
			echo "Could not find ${APP_NAME}"
		fi
	else
		pid=`cat ${CCDP_PIDFILE}`
		echo "Stopping ${APP_NAME}: $pid"
		kill -9 $pid		
		rm -f ${CCDP_PIDFILE}	
	fi
	echo "."
	;;

	restart)
	echo "    Restarting ${APP_NAME}"
	/bin/bash ${CCDP_HOME}/bin/run_app.sh stop
	/bin/bash ${CCDP_HOME}/bin/run_app.sh start

	echo "."

	;;
	status)
	#			   Find the app name       avoid grep    and java     and this pid         or the script    get the PID
	pid=`ps aux | grep ${SRCH_APP_NAME} | grep -v grep | grep java | grep -v ${MY_PID} | grep -v $0 | awk '{print $2}'`


	if [ "${pid}" != "" ] ; then
		echo ""
		echo "    ${APP_NAME} is running (PID: $pid)"
		echo ""
	else
		echo ""
		echo "    ${APP_NAME} is NOT running"
		echo ""
	fi
	;;
	
	*)
	echo ""
	echo "    USAGE: $0 { start | stop | restart | status }"
	echo ""
	exit 1
	;;
esac

exit 0

