#!/bin/bash

source env.sh

git remote add project git@github.com:ryan-thurber/chipyard.git
git fetch project

git config --global user.email "kmunifahmed@arizona.edu"
git config --global user.name "Khandoker Munif Ahmed"

# Setup the symbolic link to get dramSim2 to work
ln -s /workspace/chipyard/generators/testchipip/src/main/resources/dramsim2_ini/ /workspace/chipyard/sims/verilator/dramsim2_ini

# Build and compile test programs
cd /workspace/chipyard/tests
cmake -B build
cmake --build build