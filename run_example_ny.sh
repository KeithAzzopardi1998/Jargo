#!/bin/bash

MODE="real"

#data_dir="/data/dissertation_data/jargo_datasets/Manhattan"
data_dir="${JARGO_DATA_DIR}/Manhattan"

ROAD="${data_dir}/mny.rnet"

GTREE="${data_dir}/mny.gtree"

PROB="${data_dir}/mny-1-5000.instance"

CLIENT="solvers/jar/solvers.jar"
CLASSNAME="Exhaustive"
_CLASSPATH=.:jar/*:dep:dep/*:solvers:solvers/jar/*

#CLIENT="example/jar/examples.jar"
#CLASSNAME="com.github.jargors.client.GreedyInsertion"
#_CLASSPATH=.:jar/*:dep:dep/*:example:example/jar/*

#time_start=10
#time_end=30

echo "running in $MODE mode"
time source ./launch-cli.sh $MODE $ROAD $GTREE $PROB $CLIENT $CLASSNAME
