# STREAM run results

Store each simulator output and a metadata note in this directory. Include the
STREAM source revision, `StreamBaselineConfig`, compiler flags, array size,
offset, and the validation result. The benchmark reports the best cycles per
element from iterations after the first; it does not report standard STREAM
wall-clock bandwidth.

From the Chipyard container, build and disassemble with:

```sh
source env.sh
make -C /workspace/student-work/baselinePIM
make -C /workspace/student-work/baselinePIM dump
```

Build the simulator from `/workspace/chipyard/sims/verilator` with:

```sh
make CONFIG=StreamBaselineConfig
```

Then run the executable with the generated `simulator-chipyard.harness-StreamBaselineConfig`.
Copy the run output into this directory on the host so it persists with the
repository.
