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
  

  val src_addr  = RegInit(0.U(64.W))
  val dst_addr  = RegInit(0.U(64.W))
  val length    = RegInit(0.U(32.W))
  val status    = RegInit(0.U(32.W))
  val control   = RegInit(false.B)

  val irqWire = RegInit(false.B)
  interrupts(0) := irqWire

  outer.mmioNode.regmap(
    0x00 -> Seq(RegField(64, src_addr,  RegFieldDesc("src_addr",  "DMA source address"))),
    0x08 -> Seq(RegField(64, dst_addr,  RegFieldDesc("dst_addr",  "DMA destination address"))),
    0x10 -> Seq(RegField(32, length,    RegFieldDesc("length",    "Transaction length"))),
    0x14 -> Seq(RegField(32, status,    RegFieldDesc("status",    "DMA Status"))),
    0x18 -> Seq(RegField(1,  control,   RegFieldDesc("control",   "DMA Control"))),
    )

  val s_idle :: s_read_req :: s_read_resp :: s_write_req :: s_write_resp :: s_done :: Nil = Enum(6)
  val state = RegInit(s_idle)

  val dataBuffer = Reg(UInt(64.W))
  val bytesRemaining = RegInit(0.U(32.W))

  switch(state) {

    is(s_idle) {
      status := 0.U
      when(control === true.B) {
        bytesRemaining := length
        status  := 1.U
        state := s_read_req
      }
    }

    is(s_read_req) {
      dmaOut.a.valid := true.B
      dmaOut.a.bits  := dmaEdge.Get(
        fromSource = 0.U,
        toAddress = src_addr,
        lgSize = 4.U
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
        lgSize = 4.U,
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
      status  := 0.U
      control := false.B
      irqWire := true.B
      state   := s_idle
    }
  }

}

trait CanHavePeripheryTestDMA { this: BaseSubsystem =>
  
  private val pbus = locateTLBusWrapper(PBUS)
  private val fbus = locateTLBusWrapper(FBUS)

  p(TestDMAKey).foreach { params =>
    val testDMA = fbus { LazyModule(new TestDMA(params)) }
    pbus.coupleTo("testdma-cfg") { testDMA.mmioNode := TLFragmenter(pbus.beatBytes, pbus.blockBytes) := _ }
    fbus.coupleFrom("testdma-stream") { _ := testDMA.dmaNode }
    ibus.fromSync := testDMA.intNode
  }
}

                              
