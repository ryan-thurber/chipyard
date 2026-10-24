#include <stdio.h>
#include <riscv-pk/encoding.h>
#include <stdint.h>

int main(void) {
  
  int LENGTH = 64;
  int arr[LENGTH];
  
  for (int i = 0; i < LENGTH; i++)
  {  
	printf("Memory Address: %p - Value: %d\n", (void*)&arr[i], arr[i]);
  }
  
  return 0;
}