#!/bin/bash
# ALWAYS specify CPU and RAM resources needed, as well as walltime
#SBATCH --partition=cpuonly
#SBATCH --ntasks=1
#SBATCH --nodes=1
#SBATCH --cpus-per-task=6
#SBATCH --mem-per-cpu=3G
# simulations take 27 hrs to run, and we give a 30 min buffer
#SBATCH --time=1650
# email user with progress
#SBATCH --mail-user=keith.azzopardi.16@um.edu.mt
#SBATCH --mail-type=all
#SBATCH --job-name=jargosim
#SBATCH --output=slurm-$jobid.out
#SBATCH --error=slurm-$jobid.err


# This script should always be run on radagast, and 
# schedules a job to run on the other nodes

# DO NOT FORGET TO GIT PULL BEFORE RUNNING THIS
srun ~/msc_dissertation/code/runsim_uni_worker.sh