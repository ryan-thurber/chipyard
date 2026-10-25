package chipyard.example

import amba.axi4._
import chisel3._
import chisel3.util._
import freechips.rocketchip.subsystem._
import org.chipsalliance.cde.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp, IdRange}
import freechips.rocketchip.tilelink._
import testchipip.soc.{SubsystemInjector, SubsystemInjectorKey}

case class TestDMAParams(baseAddress: BigInt, beatBytes: Int = 8)

class TestDMA(params: TestDMAParams)(implicit p: Parameters) extends LazyModule {

  val mmioNode = TLRegisterNode(
    address = Seq(AddressSet(params.baseAddress, 0xfff)),
    device = new SimpleDevice("test-dma-device", Seq("ua,test-dma-device")),
    beatBytes = params.beatBytes,
    )
    
  val dmaNode = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLClientParameters(
    name = "test-dma", sourceId = IdRange(0, 1))))))

  lazy val module = new TestDMAModuleImp(this)
}

class TestDMAModuleImp(outer: TestDMA) extends LazyModuleImp(outer) {

  val (dmaOut, dmaEdge) = outer.dmaNode.out(0)
  val config = p(TestDMAKey).get

  val src_addr = RegInit(0.U(64.W))
  val dst_addr = RegInit(0.U(64.W))
  val tx_len   = RegInit(0.U(32.W))
  val dma_stat = RegInit(0.U(32.W))

  outer.mmioNode.regmap(
    0x00 -> Seq(RegField(64, src_addr, RegFieldDesc("src_addr", "DMA Source Address"))),
    0x08 -> Seq(RegField(64, dst_addr, RegFieldDesc("dst_addr", "DMA Destination Address"))),
    0x10 -> Seq(RegField(32, tx_len,   RegFieldDesc("tx_len", "Transaction Length"))),
    0x14 -> Seq(RegField(32, dma_stat, RegFieldDesc("dma_stat", "DMA Status"))),
  )

  val s_idle :: s_read_req :: s_read_resp :: s_write_req :: s_write_resp :: s_done :: Nil = Enum(6)
  val state = RegInit(s_idle)

  val dataBuffer = Reg(Uint(64.W))
  val bytesRemaining = RegInit(0.U(32.W))

  val startTrigger = io.core_or_mmio_write_event

  switch(state) {

    is(s_idle) {
      status := 0.U
      when(startTrigger) {
        bytesRemaining := tx_len
        status := 1.U
        state := s_read_req
      }
    }

    is(s_read_req) {
      dmaOut.a.valid := true.B
      dmaOut.a.bits  := dmaEdge.Get(
        fromSource = 0.U,
        toAddress = src_addr,
        lgSize = 3.U
        )._2

      when(dmaOut.a.read) {
        state := s_read_resp
      }
    }

    is(s_read_resp) {
      dmaOut.d.ready := true.B
      when(dmaOut.d.valid) {
        dataBuffer := dmaOut.d.bits.data
        src_addr := src_addr + 8.U
        state := s_write_req
      }
    }

    is(s_write_req) {
      dmaOut.a.valid := true.B
      dmaOut.a.bits := dmaEdge.Put(
        fromSource = 0.U,
        toAddress = dst_addr,
        lgSize = 3.U,
        data = dataBuffer
        )._2

      when(dmaOut.a.read) {
        state := s_write_resp
      }
    }

    is(s_write_resp) {
      dmaOut.d.ready := true.B
      when(dmaOut.d.valid) {
        dst_addr := dst_addr + 8.U
        when(bytesRemaining <= 8.U) {
          state := s_done
        }.otherwise {
          bytesRemaining := bytesRemaining - 8.U
          state := s_read_req
        }
      }
    }

    is(s_done) {
      status := 2.U
      state := s_idle
    }
  }
}

trait CanHavePeripheryTestDMA { this: BaseSubsystem =>
  val testDMA = LazyModule(new TestDMA(TestDMAParams(baseAddress = 0x20000000, beatBytes = pbus.beatBytes)))

  pbus.coupleTo("testdma-cfg") { testDMA.mmioNode := TLFragmenter(pbus.beatBytes, pbus.blockBytes) := _ }

  fbus.coupleFrom("testdma-stream") { _ := testDMA.dmaNode }

}


  
