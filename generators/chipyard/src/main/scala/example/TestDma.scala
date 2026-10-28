package chipyard.example

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Parameters, Field, Config}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.regmapper._
import testchipip.soc.{SubsystemInjector, SubsystemInjectorKey}

case class TestDMAParams(baseAddress: BigInt, beatBytes: Int = 8)

case object TestDMAKey extends Field[Option[TestDMAParams]](None)

class WithTestDMA(baseAddress: BigInt = 0x20000000) extends Config((site, here, up) => {
  case TestDMAKey => Some(TestDMAParams(baseAddress = baseAddress))
})

class TestDMA(params: TestDMAParams)(implicit p: Parameters) extends LazyModule {

  val mmioNode = TLRegisterNode(
    address = Seq(AddressSet(params.baseAddress, 0xfff)),
    device = new SimpleDevice("test-dma-device", Seq("ua,test-dma-device")),
    beatBytes = params.beatBytes,
    )
    
  val dmaNode = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLClientParameters(
    name = "test-dma", sourceId = IdRange(0, 1))))))

  val intNode = IntSourceNode(IntSourcePortSimple())

  lazy val module = new TestDMAModuleImp(this)
}

class TestDMAModuleImp(outer: TestDMA) extends LazyModuleImp(outer) {

  val (dmaOut, dmaEdge) = outer.dmaNode.out(0)

  val (interrupts, _) = outer.intNode.out(0)
  //val irqWire = interrupts(0)

  val src_addr  = RegInit(0.U(64.W))
  val dst_addr  = RegInit(0.U(64.W))
  val tx_len    = RegInit(0.U(32.W))
  val dma_begin = RegInit(0.U(8.W))
  val dma_busy  = RegInit(0.U(8.W))
  val dma_done  = RegInit(0.U(8.W))
  val dma_dunno = RegInit(0.U(8.W))
  //val int_enable = RegInit(false.B)

  outer.mmioNode.regmap(
    0x00 -> Seq(RegField(64, src_addr,  RegFieldDesc("src_addr",  "DMA source address"))),
    0x08 -> Seq(RegField(64, dst_addr,  RegFieldDesc("dst_addr",  "DMA destination address"))),
    0x10 -> Seq(RegField(32, tx_len,    RegFieldDesc("tx_len",    "Transaction length"))),
    0x14 -> Seq(RegField(8,  dma_begin, RegFieldDesc("dma_begin", "Begin DMA execution"))),
    0x15 -> Seq(RegField(8,  dma_busy,  RegFieldDesc("dma_busy",  "DMA is executing"))),
    0x16 -> Seq(RegField(8,  dma_done,  RegFieldDesc("dma_done",  "DMA finished execution"))),
    0x17 -> Seq(RegField(8,  dma_dunno, RegFieldDesc("dma_dunno", "Don't remember")))
    //0x18 -> Seq(RegField(1, int_enable, RegFieldDesc("int_enable", "I don't remember right now")))
    )

  val s_idle :: s_read_req :: s_read_resp :: s_write_req :: s_write_resp :: s_done :: Nil = Enum(6)
  val state = RegInit(s_idle)

  val dataBuffer = Reg(UInt(64.W))
  val bytesRemaining = RegInit(0.U(32.W))
  
  //irqWire := dma_stat(3) && int_enable

  switch(state) {

    is(s_idle) {
      dma_busy := false.B
      when(dma_begin.orR) {
        bytesRemaining := tx_len
        dma_busy  := true.B
        dma_done  := false.B
        dma_dunno := false.B
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

      when(dmaOut.a.ready) {
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

      when(dmaOut.a.ready) {
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
      dma_begin := false.B
      dma_busy  := false.B
      dma_done  := true.B
      dma_dunno := true.B
      state := s_idle
    }
  }

}

trait CanHavePeripheryTestDMA { this: BaseSubsystem =>
  
  private val pbus = locateTLBusWrapper(PBUS)
  private val fbus = locateTLBusWrapper(FBUS)

  p(TestDMAKey).foreach { params =>
    val testDMA = LazyModule(new TestDMA(params))
    pbus.coupleTo("testdma-cfg") { testDMA.mmioNode := TLFragmenter(pbus.beatBytes, pbus.blockBytes) := _ }
    fbus.coupleFrom("testdma-stream") { _ := testDMA.dmaNode }
    ibus.fromSync := testDMA.intNode
  }
}

                              
