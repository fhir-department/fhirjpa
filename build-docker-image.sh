#!/bin/sh

mvn package && \
  docker build -t claimcdr/fhirjpa-java .

  docker push claimcdr/fhirjpa-java:latest;