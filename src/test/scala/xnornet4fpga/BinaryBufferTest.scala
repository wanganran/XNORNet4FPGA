package xnornet4fpga

import chisel3.iotesters.PeekPokeTester

import scala.util.Random

/**
  * Created by wanga on 2017/5/31.
  */
class BinaryBufferTest(hwConfig:HardwareConfig, bb:BinaryBuffer) extends PeekPokeTester(bb){
  val gc=(0 until hwConfig.maxInputWidth) map {_=>Random.nextBoolean()}


  for(t<-0 until hwConfig.maxInputWidth/hwConfig.XNORFanout) {
    for (i <- 0 until hwConfig.XNORFanout)
      poke(bb.io.in(i), gc(i))
    poke(bb.io.push, true)
    step(1)
  }

  for(t<-0 until hwConfig.XNORBitWidth){
    expect(bb.io.out(t), gc(t))
  }

  poke(bb.io.push, false)
  poke(bb.io.pop, true)

  for(i<-1 until hwConfig.maxInputWidth/hwConfig.XNORBitWidth) {
    step(1)

    for (t <- 0 until hwConfig.XNORBitWidth)
      expect(bb.io.out(t), gc(t+hwConfig.XNORBitWidth*i))
  }

  //test not push or pop
  poke(bb.io.push, false)
  poke(bb.io.pop, false)

  step(1)

  for (t <- 0 until hwConfig.XNORBitWidth)
    expect(bb.io.out(t), gc(t+hwConfig.maxInputWidth-hwConfig.XNORBitWidth))

  //test simultanuously push and pop
  poke(bb.io.push, true)
  poke(bb.io.pop, true)
  for (i <- 0 until hwConfig.XNORFanout)
    poke(bb.io.in(i), gc(i))

  step(1)

  for (t <- 0 until hwConfig.XNORBitWidth)
    expect(bb.io.out(t), gc(t))
}
