# ECE 462/562 PIM Project Group Repository

This repository is a fork of [the base course repository] (https://github.com/tosiron/ua-chipyard/tree/main) for our group to collaborate.
Go to the class repo if you have any basic questions regarding setup or running base Configurations

## Basic Workflow
There are two helper scripts to make working on the project faster, projectDockerCompose.sh and projectEnvSetup.sh
- projectDockerCompose.sh is run to start the docker container. Right now I just made it to copy any scala configurations from the student-work directory to the course/configs directory. That allows us to keep all our work in the student-work directory. We may expand the script later to do more stuff.
- projectEnvSetup.sh is run after the above script. It creates the symbolic link to where the dram files are saved in our verilator directory, as well as building and compiling the programs in tests, so we don't need to manually do that every time.


Starting from your ua-chipyard directory run the following commands:
1. Create the container
```bash
./student-work/projectDockerCompose.sh
```

2. Run the projectEnvSetup script
```bash
../student-work/projectEnvSetup.sh
```

3. Source env.sh - I couldn't get this to take effect in the main terminal from a bash script, so you still have to run this command manually.
```bash
source env.sh
```

4. Now everything is basically setup, follow the instructions from the course to configure and run a simulation. However, I also haven't figured out how to add the needed command line flags to run the sim with dramSim2 automatically, so here's the command to do that.
```bash
./simulator-chipyard.harness-[ConfigYouWantToSimulate] +permissive +dramsim +permissive-off [PathTo.riscvExecutable]
```

## Things to remember
If you create a new configuration or change a configuration name, you'll need to update projectDockerCompose.sh to make sure you copy the config into the course/configs directory.