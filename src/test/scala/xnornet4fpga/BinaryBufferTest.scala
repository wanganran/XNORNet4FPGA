package xnornet4fpga

import chisel3.iotesters.PeekPokeTester

import scala.util.Random

/**
  * Created by wanga on 2017/5/31.
  */
class BinaryBufferTest(bb:BinaryBuffer, hwConfig:HardwareConfig) extends PeekPokeTester(bb){
  val gc=(0 until hwConfig.maxInputWidth) map {_=>if(Random.nextBoolean()) 1 else 0}

  //test fast in
  val fastingc=(BigInt(Math.abs(Random.nextLong()))<<64)+BigInt(Math.abs(Random.nextLong()))

  poke(bb.io.reset, true)
  poke(bb.io.push, false)
  poke(bb.io.pop, false)
  step(1)
  poke(bb.io.reset, false)

  poke(bb.io.fastpush, true)
  print(fastingc)
  poke(bb.io.fastin, fastingc)

  step(1)
  poke(bb.io.fastpush, false)
  poke(bb.io.pop, true)
  poke(bb.io.push, false)

  for(i<-0 until 4) {
    expect(bb.io.out, (fastingc>>(32*(3-i)))&((BigInt(1)<<32)-1))
    step(1)
  }
  poke(bb.io.pop, false)

  poke(bb.io.reset, true)
  for(t<-0 until hwConfig.maxInputWidth/hwConfig.XNORFanout) {
    var t0=0
    for (i <- 0 until hwConfig.XNORFanout)
      t0=t0*2+gc(i+t*hwConfig.XNORFanout)
    poke(bb.io.in, t0)
    poke(bb.io.pop, false)
    poke(bb.io.push, true)
    step(1)

    poke(bb.io.reset, false)
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
