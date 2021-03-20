#!/usr/bin/env bash
#IMPORTANT: set this in the script
#_CLASSPATH=.:jar/*:dep:dep/*
#_CLASSPATH=.:jar/*:dep:dep/*:example:example/jar/*
#_CLASSPATH=.:jar/*:dep:dep/*:solvers:solvers/jar/*
java \
    -Xmx6g \
    -Djava.library.path=dep \
    -Dderby.language.statementCacheSize=200 \
    -Dderby.locks.deadlockTrace=true \
    -Dderby.locks.monitor=true \
    -Dderby.storage.pageCacheSize=8000 \
    -Djargors.storage.debug=false \
    -Djargors.controller.debug=true \
    -Djargors.communicator.debug=false \
    -Djargors.client.debug=true \
    -Djargors.client.dm_enable=true \
    -Djargors.algorithm.rebalance_enable=true \
    -Djargors.algorithm.debug=true \
    -Djargors.costcalculation.debug=false \
    -Djargors.traffic.debug=false \
    -cp $_CLASSPATH:$DERBY_HOME/lib/derby.jar \
com.github.jargors.ui.Command $@


