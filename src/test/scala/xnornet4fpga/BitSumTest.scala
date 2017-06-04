package xnornet4fpga

import chisel3.iotesters.PeekPokeTester
import chisel3.util._
import chisel3._

import scala.util.Random

class BitSumModule extends Module{
  val io=IO(new Bundle{
    val in=Input(Bits(32.W))
    val out=Output(SInt(6.W))
  })
  io.out:=BitSum(32, 6).generate(io.in)
}

/**
  * Created by wanga on 2017/5/31.
  */
class BitSumTest(bs:BitSumModule) extends PeekPokeTester(bs) {
  val data=(0 until 32).map{_=>if(Random.nextBoolean())1 else 0}
  val result=data.map(_*2-1).sum
  val v=(31 to 0 by -1).foldLeft(BigInt(0)){case (acc, v)=>acc*2+data(v)}
  for(i<-0 until 32)
    poke(bs.io.in, v)
  expect(bs.io.out, result)
}
