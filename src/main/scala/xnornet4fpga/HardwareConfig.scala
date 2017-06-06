package xnornet4fpga

/**
  * Created by wanganran on 5/28/17.
  */

case class HardwareConfig(memSize:Int,
                          memLineWidth:Int,
                          memAddrWidth:Int,
                          memCnt:Int,
                          XNORBitWidth:Int,
                          XNORFanout:Int,
                          maxInputWidth:Int,
                          maxFeatures:Int,
                          bnParamWidth:Int,
                          bsWidth:Int,
                          opWidth:Int,
                          resultWidth:Int
                         ){
  //check bandwidth limitation
  assert(memLineWidth*memCnt>=XNORFanout*XNORBitWidth)
  //spare bandwidth for later parameters
  val spareBandwidth=memLineWidth*memCnt-XNORFanout*XNORBitWidth
}
object HardwareConfig{
  val twoway=HardwareConfig(512, 9, 9, 8, 32, 2, 256, 96, 16, 10, 32, 4)
  val default=HardwareConfig(256, 18, 8, 8, 32, 4, 256, 128, 16, 10, 32, 4)
}
