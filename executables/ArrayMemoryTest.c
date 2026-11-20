#include <stdio.h>
#include <riscv-pk/encoding.h>
#include <stdint.h>

#define DMA_BASE          0x20000000
#define DMA_SRC_REG1       (DMA_BASE + 0x00)   // 64-bit
#define DMA_SRC_REG2       (DMA_BASE + 0x08)   // 64-bit
#define DMA_DST_REG        (DMA_BASE + 0x10)   // 64-bit
#define DMA_LENGTH         (DMA_BASE + 0x18)   // 32-bit
#define DMA_STATUS         (DMA_BASE + 0x1C)   // 32-bit, read-only: 0=idle 1=busy 2=done
#define DMA_CONTROL        (DMA_BASE + 0x20)   // bit 0 = start
#define DMA_IRQ            (DMA_BASE + 0x24)   // bit 0 = irq pending, write 0 to clear

#define DMA_STATUS_DONE   2

// PLIC source ID of the DMA. Assigned by diplomacy -- VERIFY this against
// "interrupts = <N>" in the test-dma-device node of the generated .dts file
// (sims/verilator/generated-src/<config>/*.dts).
#define DMA_IRQ_ID        1

#define PLIC_BASE         0x0C000000
#define PLIC_PRIORITY(n)  (PLIC_BASE + 4 * (n))
#define PLIC_ENABLE_0     (PLIC_BASE + 0x2000)     // hart 0, M-mode context
#define PLIC_THRESHOLD    (PLIC_BASE + 0x200000)
#define PLIC_CLAIM        (PLIC_BASE + 0x200004)

#define REG32(addr)       (*(volatile uint32_t*)(uintptr_t)(addr))
#define REG64(addr)       (*(volatile uint64_t*)(uintptr_t)(addr))

volatile int dma_completed = 0;

// mtvec's low 2 bits select the trap mode, so the handler must be 4-byte aligned.
void __attribute__((interrupt, aligned(4))) handle_trap(void) {
    uintptr_t mcause = read_csr(mcause);

    // Interrupts have the MSB of mcause set; exceptions do not.
    if ((intptr_t)mcause < 0 && (mcause & 0xFF) == IRQ_M_EXT) {
        uint32_t int_id = REG32(PLIC_CLAIM);   // claim

        if (int_id == DMA_IRQ_ID) {
            REG32(DMA_IRQ) = 0;                // deassert the level-sensitive line first
            dma_completed = 1;
        }

        REG32(PLIC_CLAIM) = int_id;            // complete
    }
}

// TileLink transfers must be aligned to their size (8 bytes per DMA beat).
// dst_buf is volatile because the compiler cannot see the DMA writing to it.
static uint32_t src_buf1[4] __attribute__((aligned(16))) =
    {0x11111111, 0x11111111, 0x11111111, 0x11111111};
static uint32_t src_buf2[4] __attribute__((aligned(16))) =
    {0x22222222, 0x33333333, 0x44444444, 0x55555555};
static volatile uint32_t dst_buf[4] __attribute__((aligned(16))) = {0, 0, 0, 0};

int main(void) {

    // 1. Setup trap vector (direct mode)
    write_csr(mtvec, &handle_trap);

    // 2. Configure PLIC
    REG32(PLIC_PRIORITY(DMA_IRQ_ID)) = 1;           // priority > 0
    REG32(PLIC_ENABLE_0) |= (1u << DMA_IRQ_ID);      // enable source
    REG32(PLIC_THRESHOLD) = 0;                       // allow all priorities > 0

    // 3. Enable CPU external interrupts
    set_csr(mie, MIP_MEIP);
    set_csr(mstatus, MSTATUS_MIE);

    // 4. Program and start the DMA (64-bit stores for the 64-bit registers)
    REG64(DMA_SRC_REG1) = (uintptr_t)src_buf1;
    REG64(DMA_SRC_REG2) = (uintptr_t)src_buf2;
    REG64(DMA_DST_REG) = (uintptr_t)dst_buf;
    REG32(DMA_LENGTH)  = sizeof(src_buf1);
    REG32(DMA_CONTROL) = 0x1;                        // start

    // 5. Wait for the interrupt handler to set the flag
    while (!dma_completed) {
        asm volatile("wfi" ::: "memory");
    }

    // 6. Sanity-check the status register
    if (REG32(DMA_STATUS) != DMA_STATUS_DONE) {
        printf("DMA status not DONE: %u\n", REG32(DMA_STATUS));
        return 2;
    }

    // 7. Verify results
    for (int i = 0; i < 4; i++) {
        if (dst_buf[i] != src_buf1[i] + src_buf2[i]) {
            printf("Mismatch at %d: got 0x%08x expected 0x%08x\n",
                   i, dst_buf[i], src_buf1[i] + src_buf2[i]);
            return 1;
        }
		// I'd like visual proof that both buffers contain the same value
		else {
			printf("Index: %d\n", i);
			printf("Source Buffer 1    - 0x%08x\n", src_buf1[i]);
			printf("Source Buffer 2    - 0x%08x\n", src_buf2[i]);
			printf("Destination Buffer - 0x%08x\n", dst_buf[i]);
		}
    }

    printf("DMA test passed\n");
    return 0;
}
