package chipyard.example

import chisel3._
import chisel3.util._
import freechips.rocketchip.subsystem._
import org.chipsalliance.cde.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp, IdRange}
import freechips.rocketchip.tilelink._
import testchipip.soc.{SubsystemInjector, SubsystemInjectorKey}

case class  VectorCopyConfig(controlAddress: BigInt, beatBytes: Int = 8)
case object VectorCopyKey extends Field[Option[VectorCopyConfig]](None)

class VectorCopy(implicit p: Parameters) extends LazyModule {

	val clientNode = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLClientParameters(
		name = "vector-copy-client", sourceId = IdRange(0,1))))))
		
	val device = new SimpleDevice("vector-copy", Seq("vectorcopy"))
	val registerNode = TLRegisterNode(Seq(AddressSet(p.controlAddress)), device, "reg/control", beatBytes=p.beatBytes)
		
	lazy val module = new VectorCopyModuleImp(this)
}

class VectorCopyModuleImp(outer: VectorCopy) extends LazyModuleImp(outer) {



}

case object VectorCopyInjector extends SubsystemInjector((p, baseSubsystem) => {

	p(VectorCopyKey).map { k =>
		implicit val q: Parameters = p
		
		val pbus = baseSubsystem.locateTLBusWrapper (PBUS)
		val fbus = baseSubsystem.locateTLBusWrapper(FBUS)
		
		val device = pbus { LazyModule(New VectorCopy(k)(p)) }
		
		pbus.coupleTo("vector-copy-dma-ctrl") {
			device.registerNode := TLFragmenter(pbus.beatBytes, pbus.blockBytes) := _
		}
		
		fbus.coupleFrom("vector-copy-dma-mem") { _ := vectorCopy.clientNode }
	}
}