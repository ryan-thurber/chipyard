// ECE 462/562 - Baseline RocketCore configuration (standalone)
//
// Build from chipyard/sims/verilator:
//   make CONFIG=BaselineConfig
//
package chipyard

import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._
import testchipip.dram._


// ---------------------------------------------------------------------------
// The baseline tile
//
// A single in-order 5-stage RocketCore with every optional performance
// structure either removed or set to its cheapest legal setting:
//   * btb        = None  -> no branch prediction of any kind. Every taken
//                           branch/jump is a front-end bubble.
//   * fpu        = Trying to add one
//   * mulDiv           -> 1 bit per cycle, no early-out. A 64-bit multiply
//                           takes ~64 cycles instead of ~8.
//   * 4 KiB direct-mapped L1 I$ and D$ (64 sets x 1 way x 64 B).
//     4 KiB is the largest legal direct-mapped L1 here: nSets * blockBytes
//     must not exceed pageSize * nWays (64 * 64 == 4096).
//   * nMSHRs     = 0     -> blocking data cache.
//   * nBreakpoints = 0   -> no hardware debug breakpoint comparators.
// ---------------------------------------------------------------------------
class WithTestBaselineCore(
  n: Int,
  location: HierarchicalLocation,
  crossing: RocketCrossingParams,
) extends Config((site, here, up) => {
  case TilesLocated(`location`) => {
    val prev = up(TilesLocated(`location`), site)
    val idOffset = up(NumTiles)
    val baseline = RocketTileParams(
      core = RocketCoreParams(
        fpu = Some(FPUParams(
          minFLen = 32,
          fLen = 64,
          divSqrt = true,
          sfmaLatency = 3,
          dfmaLatency = 4,
          fpmuLatency = 2,
          ifpuLatency = 2)),                          // no FPU
        nBreakpoints = 0,                    // no debug breakpoints
        mulDiv = Some(MulDivParams(          // slowest legal multiplier/divider
          mulUnroll = 1,
          mulEarlyOut = false,
          divEarlyOut = false))),
      btb = None,                            // no branch prediction at all
      dcache = Some(DCacheParams(
        nSets = 64,                          // 64 * 1 * 64 B = 4 KiB
        nWays = 1,
        rowBits = site(SystemBusKey).beatBits,
        nMSHRs = 0,                          // blocking D$
        blockBytes = site(CacheBlockBytes))),
      icache = Some(ICacheParams(
        nSets = 64,                          // 64 * 1 * 64 B = 4 KiB
        nWays = 1,
        rowBits = site(SystemBusKey).beatBits,
        blockBytes = site(CacheBlockBytes))))
    List.tabulate(n)(i => RocketTileAttachParams(
      baseline.copy(tileId = i + idOffset),
      crossing
    )) ++ prev
  }
  case NumTiles => up(NumTiles) + n
}) {
  def this(n: Int = 1, location: HierarchicalLocation = InSubsystem) =
    this(n, location, RocketCrossingParams(
      master = HierarchicalElementMasterPortParams.locationDefault(location),
      slave = HierarchicalElementSlavePortParams.locationDefault(location),
      mmioBaseAddressPrefixWhere = location match {
        case InSubsystem => CBUS
        case InCluster(clusterId) => CCBUS(clusterId)
      }
    ))
}

/** No branch prediction, we have an FPU, no L2, 4 KiB direct-mapped L1s.
  *
  * chipyard.config.WithBroadcastManager replaces the 512 KiB inclusive LLC
  * that AbstractConfig instantiates with a bufferless broadcast coherence hub,
  * which keeps the DRAM port working but adds no last-level cache. */
class TestConfig extends Config(
  new chipyard.config.WithNPerfCounters(8) ++
  new WithTestBaselineCore(1) ++
  new chipyard.config.AbstractConfig
)

