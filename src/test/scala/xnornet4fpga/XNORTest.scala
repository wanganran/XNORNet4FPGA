package xnornet4fpga

import chisel3.iotesters.PeekPokeTester

import scala.util.Random

/**
  * Created by wanga on 2017/5/31.
  */
class XNORTest(hwConfig:HardwareConfig, xnor: XNOR) extends PeekPokeTester(xnor) {
  //generate test
  val bin1=(0 until hwConfig.XNORBitWidth) map {_=>Random.nextBoolean()}
  val bin2=(0 until hwConfig.XNORFanout) map {_=>(0 until hwConfig.XNORBitWidth) map {_=>Random.nextBoolean()}}

  val binR=(0 until hwConfig.XNORFanout) map {i=>bin1.zipWithIndex.map { case (x, id)=> !(x^bin2(i)(id))} }

  poke(xnor.io.en, true)
  for(i<-0 until bin1.length)
    poke(xnor.io.in1(i), bin1(i))

  for(i<-0 until bin2.length)
    for(j<-0 until bin2(0).length)
      poke(xnor.io.in2(i)(j), bin2(i)(j))

  step(1)

  for(i<-0 until bin2.length)
    for(j<-0 until bin2(0).length)
      expect(xnor.io.out(i)(j), binR(i)(j))
}
