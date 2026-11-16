# STREAM's student-owned Chipyard build. The official source is adapted to
# report mcycle-derived cycles/element; it is not a standard wall-time result.
RISCV_PREFIX ?= riscv64-unknown-elf-
CC      := $(RISCV_PREFIX)gcc
OBJDUMP := $(RISCV_PREFIX)objdump

ARCH    ?= rv64imafd
ABI     ?= lp64d
CMODEL  ?= medany
SPECS   ?= htif_nano.specs
LDSCRIPT ?= htif.ld
ARRAY_SIZE ?= 10000
NTIMES ?= 3
OUTDIR  ?= /workspace/output

CFLAGS  := -std=gnu99 -O2 -Wall -Wextra -fno-common -fno-builtin-printf \
           -march=$(ARCH) -mabi=$(ABI) -mcmodel=$(CMODEL) -specs=$(SPECS) \
           -DSTREAM_ARRAY_SIZE=$(ARRAY_SIZE)
LDFLAGS := -static -T $(LDSCRIPT)

ELF  := $(OUTDIR)/stream.riscv
DUMP := $(OUTDIR)/stream.dump

.PHONY: all dump clean
all: $(ELF)

$(OUTDIR):
	mkdir -p $@

$(ELF): stream.c | $(OUTDIR)
	$(CC) $(CFLAGS) $< -o $@ $(LDFLAGS)

dump: $(DUMP)

$(DUMP): $(ELF)
	$(OBJDUMP) -D $< > $@

clean:
	rm -f $(ELF) $(DUMP)
