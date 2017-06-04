package xnornet4fpga

import chisel3.iotesters.PeekPokeTester

import scala.util.Random

/**
  * Created by wanga on 2017/6/3.
  */
class MaxBufferTest(mb:MaxBuffer, n:Int) extends PeekPokeTester(mb) {
  val gt1=Array.fill(n){ Random.nextInt(10000) }
  val max1=gt1.zipWithIndex.maxBy{case (v, idx)=>v }._2

  //test reset
  poke(mb.io.en, true)
  poke(mb.io.reset, true)
  poke(mb.io.offset, 0)
  gt1.zipWithIndex.foreach{ case (v, idx)=>poke(mb.io.in(idx), v) }

  step(1)

  expect(mb.io.out, max1)

  //test normal
  poke(mb.io.reset, false)
  poke(mb.io.offset, n)

  val gt2=Array.fill(n){Random.nextInt(10000)}
  val max2=(gt1++gt2).zipWithIndex.maxBy{ case (v, idx)=>v }._2

  gt2.zipWithIndex.foreach { case (v, idx)=> poke(mb.io.in(idx), v)}

  step(1)

  expect(mb.io.out, max2)

}
