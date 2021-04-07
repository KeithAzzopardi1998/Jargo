Docker build:

docker build -t keithazzopardium/jargo:3.2 .

Docker run simulation:

docker run \
    -it \
    --name jargo_sim \
    --memory 12g \
    --cpus 6 \
    keithazzopardium/jargo:3.2 \
    bash

Running a simulation:

    ./run_example_simonetto.sh sim-100pc-c4.instance 2>&1 | tee sim.log