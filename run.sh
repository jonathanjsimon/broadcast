#!/bin/bash

java -Djava.net.preferIPv4Stack=true -cp `cat .classpath` BroadcastTest "$@"
