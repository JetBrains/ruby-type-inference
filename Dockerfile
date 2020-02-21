FROM openjdk:8-jdk

RUN apt-get update -qq && \
    apt-get upgrade -y && \
    apt-get install -y ruby-full gcc make openjfx

RUN gem install debase-ruby_core_source
RUN gem install arg_scanner

RUN mkdir /app
WORKDIR /app
ENV PATH="./bin:${PATH}"
ADD . /app

# This was added in an attempt to get logging working. It didn't help, so it has been commented out.
#RUN apt-get install -y curl
#RUN curl -s https://www.slf4j.org/dist/slf4j-1.7.25.tar.gz | tar zxvf - slf4j-1.7.25/slf4j-simple-1.7.25.jar
#RUN mv slf4j-1.7.25/slf4j-simple-1.7.25.jar /usr/lib
#ENV CLASSPATH=.:/usr/lib/slf4j-simple-1.7.25.jar

CMD ["./gradlew","contract-creator:runServer"]

# Build the container:
# docker build -t jet_brains:ruby_type_inference .
# Run the server:
# docker run -v ${PWD}:/app -it jet_brains:ruby_type_inference 
# Drop into bash shell
# docker run -v ${PWD}:/app -it jet_brains:ruby_type_inference bash

