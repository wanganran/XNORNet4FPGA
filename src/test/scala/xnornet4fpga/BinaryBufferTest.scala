package xnornet4fpga

import chisel3.iotesters.PeekPokeTester

import scala.util.Random

/**
  * Created by wanga on 2017/5/31.
  */
class BinaryBufferTest(bb:BinaryBuffer, hwConfig:HardwareConfig) extends PeekPokeTester(bb){
  val gc=(0 until hwConfig.maxInputWidth) map {_=>if(Random.nextBoolean()) 1 else 0}


  for(t<-0 until hwConfig.maxInputWidth/hwConfig.XNORFanout) {
    var t0=0
    for (i <- 0 until hwConfig.XNORFanout)
      t0=t0*2+gc(i+t*hwConfig.XNORFanout)
    poke(bb.io.in, t0)
    poke(bb.io.push, true)
    if(t==0) poke(bb.io.reset, true)
    else poke(bb.io.reset, false)
    step(1)
  }

  var r=BigInt(0)
  for(t<-0 until hwConfig.XNORBitWidth)
    r=r*2+gc(t)

  expect(bb.io.out, r)

  poke(bb.io.push, false)
  poke(bb.io.pop, true)

  for(i<-1 until hwConfig.maxInputWidth/hwConfig.XNORBitWidth) {
    step(1)

    r=0
    for (t <- 0 until hwConfig.XNORBitWidth)
      r=r*2+gc(t+hwConfig.XNORBitWidth*i)
    expect(bb.io.out, r)
  }

  //test not push or pop
  poke(bb.io.push, false)
  poke(bb.io.pop, false)

  step(1)

  expect(bb.io.out, r)

  //test simultanuously push and pop
  poke(bb.io.push, true)
  poke(bb.io.pop, true)
  var t2=0
  for (i <- 0 until hwConfig.XNORFanout)
    t2=t2*2+gc(i)
  poke(bb.io.in, t2)

  step(1)

  r=0
  for (t <- 0 until hwConfig.XNORBitWidth)
    r=r*2+gc(t)
  expect(bb.io.out, r)
}
