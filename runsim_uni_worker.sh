#!/bin/bash
SIMULATION_GROUP="pre_maxn"
SIMULATION_NAME="PM12"
SIMULATION_DIR_GLOBAL="/opt/local/data/keith_azzopardi/simulations"

# this script is meant to be run from a clone of the git
# repository inside the home directory.

#the algorithm to use (baseline/sampling/routing)
param_variant="baseline"
#the demand model to use (dnn/frequentist)
param_dm_type="dnn"
param_dm_height=20
param_dm_width=5

#defining the simulation parameters
echo "defining parameters"
param_maxn=16
param_instance="sim-10pc-c10.instance"
param_rebalancing="false"
param_intances_dir="${SIMULATION_DIR_GLOBAL}/test_instances"
param_overwrite_instances="" #set to --overwrite_instances to overwrite
param_venv_dir="${SIMULATION_DIR_GLOBAL}/inference_env"
param_overwrite_venv=""      #set to --overwrite_venv to overwrite
param_results_dir="/opt/users/kazz0036/msc_dissertation/results/${SIMULATION_GROUP}/${SIMULATION_NAME}_JOB${SLURM_JOBID}"

# Before running the simulation, we copy over the repository to
# a temporary folder on /opt, so that we can run multiple simulations
# concurrently
echo "creating temporary folder"
temp_dir="${SIMULATION_DIR_GLOBAL}/temp_folders/${SIMULATION_NAME}-$(date +'%F-%T')"
mkdir -p "${temp_dir}"
cp -Rf . "${temp_dir}/."

old_dir=${PWD}
cd "${temp_dir}"

#compiling the code
echo "cleaning up binaries"
make clean
echo "fetching dependencies"
make dep
echo "creating Jargo .jar"
make jar
echo "creating solvers .jar"
cd solvers
make clean
make
cd ..
echo "running simulation"
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

#cd ${old_dir}
