#!/usr/bin/env bash

echo Step 1/4 Build WAR file
    mvn clean install;


echo Step 2/4: Creating new production image;
  mv Dockerfile.prod.off Dockerfile;
    docker build -t claimcdr/fhirjpa-java . ;
  mv Dockerfile Dockerfile.prod.off;


echo Step 3/4: Push image to Docker Hub repo;
  docker push claimcdr/fhirjpa-java:latest;


echo Step 4/4: Deploy to AWS
  eb deploy
