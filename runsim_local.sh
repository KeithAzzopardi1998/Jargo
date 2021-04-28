#!/bin/bash

#the algorithm to use
algorithm="baseline"

#Jargo parameters
param_maxn=8
param_instance="test_sim_15mins.instance"
param_rebalancing="true"
param_dm_enable="true"
param_solver="${algorithm}.CostComputationModule"
param_jar_file="${algorithm}.jar"
param_intances_dir="/home/keith/Dissertation/test_instances"
param_overwrite_instances="" #set to --overwrite_instances to overwrite
param_venv_dir="/home/keith/Dissertation/inference_env"
param_overwrite_venv=""      #set to --overwrite_venv to overwrite
param_results_dir="/home/keith/Dissertation/results/test8"

simulation_dir="/home/keith/Dissertation/github/jargo"

old_dir=${PWD}

cd "${simulation_dir}"

./run_simulation.sh \
    --maxn "${param_maxn}" \
    --instance "${param_instance}" \
    --rebalancing "${param_rebalancing}" \
    --demand_model_enable "${param_dm_enable}" \
    --jar "${param_jar_file}" \
    --solver "${param_solver}" \
    --dir_results "${param_results_dir}" \
    --dir_venv "${param_venv_dir}" \
    --dir_instances "${param_intances_dir}" \
    "${param_overwrite_instances}" \
    "${param_overwrite_venv}"

cd "${old_dir}"