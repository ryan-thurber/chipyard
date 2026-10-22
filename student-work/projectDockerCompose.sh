#!/bin/bash

# Copy the Configs we want from the student-work directory into the course/configs/ directory
cp student-work/TestConfig.scala course/configs/

# Run docker compose
docker compose run --rm chipyard