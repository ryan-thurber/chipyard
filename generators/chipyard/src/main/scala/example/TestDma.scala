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

  val device = new SimpleDevice("test-dma-device", Seq("ua,test-dma-device"))

  val mmioNode = TLRegisterNode(
    address = Seq(AddressSet(params.baseAddress, 0xfff)),
    device = device,
    beatBytes = params.beatBytes,
    )

  val dmaNode = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLClientParameters(
    name = "test-dma", sourceId = IdRange(0, 1))))))

  val intNode = IntSourceNode(IntSourcePortSimple(num = 1, resources = device.int))

  lazy val module = new TestDMAModuleImp(this)
}

class TestDMAModuleImp(outer: TestDMA) extends LazyModuleImp(outer) {

  val (dmaOut, dmaEdge) = outer.dmaNode.out(0)
  val (interrupts, _) = outer.intNode.out(0)

  // Status encodings (read-only to software)
  val STATUS_IDLE = 0.U(32.W)
  val STATUS_BUSY = 1.U(32.W)
  val STATUS_DONE = 2.U(32.W)

  val src_addr1  = RegInit(0.U(64.W))
  val src_addr2  = RegInit(0.U(64.W))
  val dst_addr   = RegInit(0.U(64.W))
  val length     = RegInit(0.U(32.W))
  val status     = RegInit(STATUS_IDLE)
  val control    = RegInit(false.B)

  // Level-sensitive interrupt line. Set when a transfer finishes, cleared by
  // software writing 0 to the IRQ register (or by starting a new transfer).
  val irqWire = RegInit(false.B)
  interrupts(0) := irqWire

  // NOTE: 64-bit fields must be written with 64-bit stores. The regmapper
  // ignores writes that do not cover every byte of a field.
  outer.mmioNode.regmap(
    0x00 -> Seq(RegField(64, src_addr1,  RegFieldDesc("src_addr1",  "DMA source address 1 (8-byte aligned)"))),
    0x08 -> Seq(RegField(64, src_addr2,  RegFieldDesc("src_addr2",  "DMA source address 2 (8-byte aligned)"))),
    0x10 -> Seq(RegField(64, dst_addr,  RegFieldDesc("dst_addr",  "DMA destination address (8-byte aligned)"))),
    0x18 -> Seq(RegField(32, length,    RegFieldDesc("length",    "Transaction length in bytes"))),
    0x1C -> Seq(RegField.r(32, status,  RegFieldDesc("status",    "DMA status: 0=idle, 1=busy, 2=done"))),
    0x20 -> Seq(RegField(1,  control,   RegFieldDesc("control",   "Write 1 to start; self-clears on completion"))),
    0x24 -> Seq(RegField(1,  irqWire,   RegFieldDesc("irq",       "Interrupt pending; write 0 to clear"))),
    )

  // Default values for the TileLink port. Chisel requires every output to be
  // driven every cycle; the FSM below overrides these in the states that use
  // the bus (last connection wins).
  dmaOut.a.valid := false.B
  dmaOut.a.bits  := DontCare
  dmaOut.d.ready := false.B

  val s_idle :: s_read_req1 :: s_read_resp1 :: s_read_req2 :: s_read_resp2 :: s_write_req :: s_write_resp :: s_done :: Nil = Enum(8)
  val state = RegInit(s_idle)

  val dataBuffer = Reg(UInt(64.W))
  val bytesRemaining = RegInit(0.U(32.W))

  // Each transfer moves one 8-byte beat: lgSize = log2(8) = 3. This matches
  // the 64-bit dataBuffer and the +8 address increments.
  val lgBeat = 3.U

  switch(state) {

    is(s_idle) {
      when(control) {
        bytesRemaining := length
        status  := STATUS_BUSY
        irqWire := false.B
        state   := s_read_req1
      }
    }

    is(s_read_req1) {
      dmaOut.a.valid := true.B
      dmaOut.a.bits  := dmaEdge.Get(
        fromSource = 0.U,
        toAddress = src_addr1,
        lgSize = lgBeat
        )._2

      when(dmaOut.a.ready) {
        state := s_read_resp1
      }
    }

    is(s_read_resp1) {
      dmaOut.d.ready := true.B
      when(dmaOut.d.valid) {
        dataBuffer := dmaOut.d.bits.data
        src_addr1 := src_addr1 + 8.U
        state := s_read_req2
      }
    }
	
	is(s_read_req2) {
      dmaOut.a.valid := true.B
      dmaOut.a.bits  := dmaEdge.Get(
        fromSource = 0.U,
        toAddress = src_addr2,
        lgSize = lgBeat
        )._2

      when(dmaOut.a.ready) {
        state := s_read_resp2
      }
    }

    is(s_read_resp2) {
      dmaOut.d.ready := true.B
      when(dmaOut.d.valid) {
        dataBuffer := dataBuffer + dmaOut.d.bits.data
        src_addr2 := src_addr2 + 8.U
        state := s_write_req
      }
    }

    is(s_write_req) {
      dmaOut.a.valid := true.B
      dmaOut.a.bits := dmaEdge.Put(
        fromSource = 0.U,
        toAddress = dst_addr,
        lgSize = lgBeat,
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
          state := s_read_req1
        }
      }
    }

    is(s_done) {
      status  := STATUS_DONE
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
