#!/bin/bash

CMD=$@
echo "Running ${CMD}" | tee /tmp/test_out.log
$CMD  | tee /tmp/test_out.log