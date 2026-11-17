// STREAM test configuration: inherit the course baseline and enable only the
// Rocket floating-point unit required by STREAM's default double kernels.
package chipyard

import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._

class StreamBaselineConfig extends Config(
  new Config((site, here, up) => {
    case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site).map {
      case tp: RocketTileAttachParams =>
        tp.copy(tileParams = tp.tileParams.copy(
          core = tp.tileParams.core.copy(fpu = Some(FPUParams()))))
    }
  }) ++
  new WithInclusiveCache(
    capacityKB = 1024,
    nWays = 4
  ) ++
  new BaselineConfig)
