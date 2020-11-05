#!/bin/bash

MODE="real"

data_dir="/data/dissertation_data/jargo_datasets/Manhattan"

ROAD="${data_dir}/mny.rnet"

GTREE="${data_dir}/mny.gtree"

PROB="${data_dir}/mny-1-5000.instance"

#CLIENT="/home/keith/Dissertation/Jargo/jar/jargors-1.0.0.jar"
CLIENT="/home/keith/Dissertation/github/jargo/solvers/jar/solvers.jar"

#CLASSNAME="com.github.jargors.sim.Client"
#CLASSNAME="example.com.github.jargors.client.GreedyInsertion"
CLASSNAME="com.github.jargors.client.GreedyInsertion"

#time_start=10
#time_end=30

echo "running in $MODE mode"
time source ./launch-cli.sh $MODE $ROAD $GTREE $PROB $CLIENT $CLASSNAME
