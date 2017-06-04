package xnornet4fpga

import chisel3.iotesters.PeekPokeTester

import scala.util.Random

/**
  * Created by wanga on 2017/6/3.
  */
class MeanBufferTest(mb:MeanBuffer, n:Int) extends PeekPokeTester(mb) {
  val gt1=Array.fill(n){Random.nextInt(20000)-10000}
  val fc=Random.nextInt(1024)+1
  val mean1=(gt1.map(Math.abs).sum) * fc / 65536

  //test reset
  poke(mb.io.reset, true)
  poke(mb.io.cntInverse65536, fc)
  gt1.zipWithIndex.foreach { case (v, idx)=>poke(mb.io.in(idx), v)}

  step(1)

  expect(mb.io.out, mean1)

  //test multiple
  val gt2=Array.fill(n){Random.nextInt(20000)-10000}
  val mean2=(gt1++gt2).map(Math.abs).sum * fc / 65536

  poke(mb.io.reset, false)
  gt2.zipWithIndex.foreach{case (v, idx)=>poke(mb.io.in(idx), v)}

  step(1)

  expect(mb.io.out, mean2)
}
