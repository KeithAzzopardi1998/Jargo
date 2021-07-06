#!/bin/bash

#the algorithm to use
param_variant="baseline"

#the demand model to use
param_dm_type="dnn"
param_dm_height=20
param_dm_width=5

#Jargo parameters
param_maxn=8
param_instance="test_sim_15mins.instance"
param_rebalancing="true"
param_intances_dir="/home/keith/Dissertation/test_instances"
param_overwrite_instances="" #set to --overwrite_instances to overwrite
param_venv_dir="/home/keith/Dissertation/inference_env"
param_overwrite_venv=""      #set to --overwrite_venv to overwrite
param_results_dir="/data/dissertation_results/test_simulations/sim1"

simulation_dir="/home/keith/Dissertation/github/jargo"

old_dir=${PWD}

cd "${simulation_dir}"

./run_simulation.sh \
    --maxn "${param_maxn}" \
    --instance "${param_instance}" \
    --rebalancing "${param_rebalancing}" \
    --demand_model_type "${param_dm_type}" \
    --demand_model_height "${param_dm_height}" \
    --demand_model_width "${param_dm_width}" \
    --variant "${param_variant}" \
    --dir_results "${param_results_dir}" \
    --dir_venv "${param_venv_dir}" \
    --dir_instances "${param_intances_dir}" \
    "${param_overwrite_instances}" \
    "${param_overwrite_venv}"

cd "${old_dir}"