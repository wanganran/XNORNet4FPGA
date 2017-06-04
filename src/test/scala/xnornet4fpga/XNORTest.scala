package xnornet4fpga

import chisel3.iotesters.PeekPokeTester

import scala.util.Random

/**
  * Created by wanga on 2017/5/31.
  */
class XNORTest(hwConfig:HardwareConfig, xnor: XNOR) extends PeekPokeTester(xnor) {
  //generate test
  val bin1=Random.nextInt(Int.MaxValue)
  val bin2=(0 until hwConfig.XNORFanout) map {_=>Random.nextInt(Int.MaxValue)}

  val binR=(0 until hwConfig.XNORFanout) map { i => ((1L<<32)-1)&(~(bin1.asInstanceOf[Long] ^ bin2(i)))}

  poke(xnor.io.in1, bin1)

  for(i<-0 until bin2.length)
    poke(xnor.io.in2(i), bin2(i))

  for(i<-0 until bin2.length)
    expect(xnor.io.out(i), binR(i))
}
