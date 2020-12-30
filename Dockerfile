FROM ubuntu:18.04

ENV JARGO_DIR="/jargo"
WORKDIR ${JARGO_DIR}

#get required ubuntu packages
RUN apt-get update && apt-get install -y \
  git \
  make \
  bash \
  wget \
  vim \
  unzip \
  screen \
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
    && tar xvf "/home/derby.tar.gz" -C "/home/derby" --strip-components 1
ENV DERBY_HOME="/home/derby"
ENV PATH="${PATH}:${DERBY_HOME}/bin"

#build the jargo executable
RUN make jar

#build the executables for the solving algorithms
RUN cd solvers && make

#get the files requires to run the simulations
ENV JARGO_DATA_DIR=/jargo_datasets
RUN git clone "https://github.com/KeithAzzopardi1998/Datasets.git" "${JARGO_DATA_DIR}"
RUN wget "https://dissertationws8191868266.blob.core.windows.net/jargo-gtree-files/mny.gtree" --output-document "/home/mny.gtree" \
    && cp "/home/mny.gtree" "${JARGO_DATA_DIR}/Manhattan" \
    && cp "/home/mny.gtree" "${JARGO_DATA_DIR}/Simonetto"

RUN chmod 777 ${JARGO_DIR}
RUN chmod 777 ${JARGO_DATA_DIR}

CMD ["bash"]