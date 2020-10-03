#!/bin/bash

MODE="real"

data_dir="/media/keith/data/dissertation_data/jargo_datasets/Chengdu-small"

ROAD="${data_dir}/cd0.rnet"

GTREE="${data_dir}/cd0.gtree"

PROB="${data_dir}/cd0-1-0002.instance"

#CLIENT="/home/keith/Dissertation/Jargo/jar/jargors-1.0.0.jar"
CLIENT="/home/keith/Dissertation/github/Jargo/example/jar/examples.jar"

#CLASSNAME="com.github.jargors.sim.Client"
#CLASSNAME="example.com.github.jargors.client.GreedyInsertion"
CLASSNAME="com.github.jargors.client.GreedyInsertion"

source ./launch-cli.sh $MODE $ROAD $GTREE $PROB $CLIENT $CLASSNAME