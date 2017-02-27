#!/bin/bash


if [ "$EUID" -ne 0 ]
  then echo "Please run as root"
  exit
fi

if [ -z ${CCDP_HOME+x} ]; then 
  echo "CCDP_HOME is unset, trying default"; 
  if [ -d "/home/oeg/dev/oeg/CCDP" ]; then
    export CCDP_HOME=/home/oeg/dev/oeg/CCDP
  else
    echo "Could not find CCDP_HOME, exitint"
  fi

else 
  echo "CCDP_HOME is set to '$CCDP_HOME'"; 
fi



JSON_CFG='{"session-id":"oeg-1","mesos-type":"MASTER","server-id":1,"clean-work-dir":1}'
echo "Setting Environment to ${JSON_CFG}"

CURR_DIR=`pwd`
cd ${CCDP_HOME}/scripts
echo "Running from $PWD"
${CCDP_HOME}/scripts/mesos_config.py ${JSON_CFG}

cd ${CURR_DIR}
