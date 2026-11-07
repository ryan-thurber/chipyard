#include <stdio.h>
#include <riscv-pk/encoding.h>
#include <stdint.h>

#define DMA_BASE          0x20000000
#define DMA_SRC_REG       (DMA_BASE + 0x00)
#define DMA_DST_REG       (DMA_BASE + 0x08)
#define DMA_LENGTH        (DMA_BASE + 0x10)
#define DMA_STATUS        (DMA_BASE + 0x14)
#define DMA_CONTROL		  (DMA_BASE + 0x18)

#define PLIC_BASE         0x0C000000
#define PLIC_PRIORITY_1   (PLIC_BASE + 0x04)
#define PLIC_PRIORITY_2   (PLIC_BASE + 0x08)
#define PLIC_PENDING_1    (PLIC_BASE + 0x1000)
#define PLIC_ENABLE_0     (PLIC_BASE + 0x2000)
#define PLIC_THRESHOLD    (PLIC_BASE + 0x200000)
#define PLIC_CLAIM        (PLIC_BASE + 0x200004)

volatile int dma_completed = 0;


void __attribute__((interrupt)) handl_trap(void) {
	
	uintptr_t mcause = read_csr(mcause);
    
    // Check if it's a Machine External Interrupt (PLIC)
    if ((mcause & MCAUSE_INT) && ((mcause & 0x7F) == IRQ_M_EXT)) {
        // Claim the PLIC interrupt
        uint32_t int_id = *(volatile uint32_t*)PLIC_CLAIM;
        
        if (int_id == 1) { // Assuming your DMA is mapped to global IRQ source 1
            dma_completed = 1;
        }
        
        // Complete (ACK) the PLIC interrupt
        *(volatile uint32_t*)PLIC_CLAIM = int_id;
    }
}

int main(void) {
	
    // 2. Setup Trap Vector
    write_csr(mtvec, &handle_trap);

    // 3. Configure PLIC (Assuming DMA is source 1)
    *(volatile uint32_t*)PLIC_PRIORITY_1 = 1;   // Set priority > 0
    *(volatile uint32_t*)PLIC_ENABLE_0 |= (1 << 1); // Enable source 1
    *(volatile uint32_t*)PLIC_THRESHOLD = 0;    // Allow all priorities > 0

    // 4. Enable CPU External Interrupts
    set_csr(mie, MIP_MEIP); // Enable Machine External Interrupts
    set_csr(mstatus, MSTATUS_MIE); // Enable Global Interrupts

    // Buffer preparation
    uint32_t src_buf[4] = {0xDEADBEEF, 0xCAFEBABE, 0x12345678, 0x9ABCDEF0};
    uint32_t dst_buf[4] = {0, 0, 0, 0};

    // 5. Trigger the DMA
    *(volatile uint32_t*)DMA_SRC_REG = (uintptr_t)src_buf;
    *(volatile uint32_t*)DMA_DST_REG = (uintptr_t)dst_buf;
    *(volatile uint32_t*)DMA_LEN_REG = sizeof(src_buf);
    *(volatile uint32_t*)DMA_CMD_REG = 0x3; // Bit 0 (Start) | Bit 1 (Interrupt Enable)

    // 6. Wait for the interrupt to modify the flag
    while (!dma_completed) {
        asm volatile("wfi"); // Wait For Interrupt (puts CPU to low power until IRQ fires)
    }

    // 7. Verify Results
    for (int i = 0; i < 4; i++) {
        if (dst_buf[i] != src_buf[i]) return 1; // Test Failed
    }
  
  return 0;
}