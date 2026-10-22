#!/bin/bash

source env.sh

# Setup the symbolic link to get dramSim2 to work
ln -s /workspace/chipyard/generators/testchipip/src/main/resources/dramsim2_ini/ /workspace/chipyard/sims/verilator/dramsim2_ini

# Build and compile test programs
cd /workspace/chipyard/tests
cmake -B build
cmake --build build