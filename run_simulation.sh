#!/bin/bash

# 0. parse the arguments

#default values
MAXN=8
INSTANCE="sim-5pc-c4.instance"
REBALANCING="true"
DEMAND_MODEL_ENABLE="true"
SOLVER="baseline"
OVERWRITE_INSTANCES="false"
OVERWRITE_VENV="false"

POSITIONAL=()
while [[ $# -gt 0 ]]
do
key="$1"

case $key in
    -m|--maxn) #integer
    MAXN="$2"
    shift # past argument
    shift # past value
    ;;
    -i|--instance) #filename
    INSTANCE="$2"
    shift # past argument
    shift # past value
    ;;
    -r|--rebalancing) #true/false
    REBALANCING="$2"
    shift # past argument
    shift # past value
    ;;
    -dm|--demand_model_enable) #true/false
    DEMAND_MODEL_ENABLE="$2"
    shift # past argument
    shift # past value
    ;;
    -s|--solver) #name of the solver to use
    SOLVER="$2"
    shift # past argument
    shift # past value
    ;;
    --dir_results) #path to the directory where to store the zip file with the results
    DIR_RESULTS="$2"
    shift # past argument
    shift # past value
    ;;
    --dir_venv) #path to the directory containing the Python virtual environment
    DIR_VENV="$2"
    shift # past argument
    shift # past value
    ;;
    --overwrite_venv) #use to force a new download of the instances
    OVERWRITE_VENV="true"
    shift # past argument
    ;;
    --dir_instances) #path to the directory containing the Jargo instances
    DIR_INSTANCES="$2"
    shift # past argument
    shift # past value
    ;;
    --overwrite_instances) #use to force a new download of the instances
    OVERWRITE_INSTANCES="true"
    shift # past argument
    ;;
    *)    # unknown option
    POSITIONAL+=("$1") # save it in an array for later
    shift # past argument
    ;;
esac
done

echo "~~ HANDLING PYTHON VENV ~~"

# 1. if it doesn't exist already, create the python environment in the specified folder
if [[ ! -d "${DIR_VENV}" || "${OVERWRITE_VENV}" == "true" ]]; then
    mkdir -p "${DIR_VENV}"
    python3 -m venv "${DIR_VENV}"
    source "${DIR_VENV}/bin/activate"
    pip3 install -r ./demand_model_data/environment/requirements.txt
    deactivate
fi

# 2. copy over the python environment
cp -rf "${DIR_VENV}/." "./demand_model_data/environment/env"

echo "~~ HANDLING JARGO INSTANCES ~~"

# 3. if it doesn't exist already, get the Jargo instances and place them in the specified folder
if [[ ! -d "${DIR_INSTANCES}" || "${OVERWRITE_INSTANCES}" == "true" ]]; then
    mkdir -p "${DIR_INSTANCES}"
    wget "https://dissertationws8191868266.blob.core.windows.net/jargo-sim-data/manhattan.zip" --output-document "jargo_instances.zip"
    unzip "jargo_instances.zip" -d "${DIR_INSTANCES}"
    rm "jargo_instances.zip"
fi

# 4. copy over the instances
cp -rf "${DIR_INSTANCES}/." "./data/manhattan"

echo "~~ ACTIVATING ENVIRONMENT ~~"
# 5. activate virtual environment
source "./demand_model_data/environment/env/bin/activate"

# 6. run the simulation
SIMULATION_IDENTIFIER="$(basename -s .instance ${INSTANCE})_${SOLVER}_$(date +'started-%d_%m_%y-%k_%M')"

data_dir="data/manhattan"
param_road="${data_dir}/mny.rnet"
param_gtree="${data_dir}/mny.gtree"
param_instance="${data_dir}/simonetto/${INSTANCE}"
param_client="solvers/jar/solvers.jar"

echo "~~ STARTING SIMULATION ~~"
java \
    -Xmx6g \
    -Djava.library.path=dep \
    -Dderby.language.statementCacheSize=200 \
    -Dderby.locks.deadlockTrace=true \
    -Dderby.locks.monitor=true \
    -Dderby.storage.pageCacheSize=8000 \
    -Djargors.storage.debug=false \
    -Djargors.controller.debug=true \
    -Djargors.controller.max_delay=7 \
    -Djargors.controller.max_wait=7 \
    -Djargors.communicator.debug=false \
    -Djargors.client.debug=true \
    -Djargors.client.dm_enable=${DEMAND_MODEL_ENABLE} \
    -Djargors.algorithm.debug=true \
    -Djargors.algorithm.rebalance_enable=${REBALANCING} \
    -Djargors.algorithm.maxn=${MAXN} \
    -Djargors.costcalculation.debug=false \
    -Djargors.traffic.debug=false \
    -cp .:jar/*:dep:dep/*:solvers:solvers/jar/* \
com.github.jargors.ui.Command "real" ${param_road} ${param_gtree} ${param_instance} ${param_client} ${SOLVER} \
    2>&1 | tee sim.log

echo "~~ FINISHED SIMULATION ~~"
# 7. move the simulation log to the export folder
mv sim.log ./export

# 8. zip the export folder
results_zip="${SIMULATION_IDENTIFIER}.zip"
zip -r "${results_zip}" ./export

echo "~~ COPYING OVER THE RESULTS ~~"
# 9. copy over the zip file to the directory specified as a parameter
if [[ ! -d "${DIR_RESULTS}" ]]; then
    mkdir -p "${DIR_RESULTS}"
fi
cp "${results_zip}"  "${DIR_RESULTS}"