#include <stdio.h>
#include <riscv-pk/encoding.h>
#include "marchid.h"
#include <stdint.h>

int main(void) {
  
  uint64_t start_c;
  uint64_t end_c;

  uint64_t start_i;
  uint64_t end_i;

  asm volatile(
	"csrr %0, mcycle"
	: "=r"(start_c)
	: 
	: "memory"
  );


  asm volatile(
	"csrr %0, minstret"
	: "=r"(start_i)
	: 
	: "memory"
  );
	
  uint64_t marchid = read_csr(marchid);
  const char* march = get_march(marchid);
  printf("Hello world from core 0, a %s\n", march);
  
  float a = 5.5f;
  float b = 2.5f;
  float result = 0.0f;

  asm volatile(
	"flw f0, %1\n\t"
        "flw f1, %2\n\t"
	"fadd.s f2, f0, f1\n\t"
	"fsw f2, %0\n\t"
	: "=m"(result)
	: "m"(a), "m"(b)
	: "f0", "f1", "f2"
  );

  if (result == 8.0f) {
	  printf("FPU test PASSED!\n");
  }
  else {
	  printf("FPU test FAILED!\n");
  }

  asm volatile(
	"csrr %0, mcycle"
	: "=r"(end_c)
	: 
	: "memory"
  );

  asm volatile(
	"csrr %0, minstret"
	: "=r"(end_i)
	: 
	: "memory"
  );

  printf("Cycles: %lu\n", end_c - start_c);
  printf("Instructions: %lu\n", end_i - start_i);
  
  return 0;
}
