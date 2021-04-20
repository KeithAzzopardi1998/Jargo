#!/usr/bin/env bash
SIMULATION_GROUP="test_group_1"
SIMULATION_NAME="run_1"
SIMULATION_DIR_GLOBAL="/opt/local/data/keith_azzopardi/simulations"

# this script is meant to be run from a clone of the git
# repository inside the home directory.

#compiling the code
make clean
make dep
make jar
cd solvers
make clean
make
cd ..

#defining the simulation parameters
param_maxn=8
param_instance="test_sim_15mins.instance"
param_rebalancing="true"
param_dm_enable="true"
param_solver="Baseline"
param_intances_dir="${SIMULATION_DIR_GLOBAL}/test_instances"
param_overwrite_instances="" #set to --overwrite_instances to overwrite
param_venv_dir="${SIMULATION_DIR_GLOBAL}/inference_env"
param_overwrite_venv=""      #set to --overwrite_venv to overwrite
param_results_dir="~/msc_dissertation/simulations/results/${SIMULATION_GROUP}/${SIMULATION_NAME}"

# Before running the simulation, we copy over the repository to
# a temporary folder on /opt, so that we can run multiple simulations
# concurrently
temp_dir="${SIMULATION_DIR_GLOBAL}/temp_folders/${SIMULATION_NAME}-$(date +'%d_%m_%y-%k_%M')"
mkdir -p "${temp_dir}"
cp -Rf . "${temp_dir}/."

old_dir=${PWD}
cd "${temp_dir}"

./run_simulation.sh \
    --maxn "${param_maxn}" \
    --instance "${param_instance}" \
    --rebalancing "${param_rebalancing}" \
    --demand_model_enable "${param_dm_enable}" \
    --solver "${param_solver}" \
    --dir_results "${param_results_dir}" \
    --dir_venv "${param_venv_dir}" \
    --dir_instances "${param_intances_dir}" \
    "${param_overwrite_instances}" \
    "${param_overwrite_venv}"

cd ${old_dir}