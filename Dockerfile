FROM ubuntu:18.04

WORKDIR /jargo

#get required ubuntu packages
RUN apt-get update && apt-get install -y \
  git \
  make \
  bash \
  wget \
  vim \
  unzip \
  openjdk-11-jdk

#set bash as the default shell
RUN usermod --shell /bin/bash root

#copy the source files over to the image
COPY . .

#get the dependencies
RUN make dep

#install Apache Derby
RUN wget "https://archive.apache.org/dist/db/derby/db-derby-10.15.1.3/db-derby-10.15.1.3-bin.tar.gz" --output-document "/home/derby.tar.gz" \
    && mkdir "/home/derby" \
    && tar xvf "/home/derby.tar.gz" -C "/home/derby" --strip-components 1 \
    && export DERBY_HOME="/home/derby" \
    && export PATH="${PATH}:${DERBY_HOME}/bin"

#build the jargo executable
RUN make jar

#build the executables for the solving algorithms
RUN cd solvers && make

CMD ["bash"]