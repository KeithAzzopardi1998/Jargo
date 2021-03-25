Docker build:

docker build -t keithazzopardium/jargo:2.4 .

Docker run simulation:

docker run \
    -it \
    --name jargo_sim \
    --memory 8g \
    --cpus 4 \
    keithazzopardium/jargo:2.4 \
    bash