package xnornet4fpga

import chisel3.iotesters.PeekPokeTester
import chisel3._
import chisel3.util._

import scala.util.Random

/**
  * Created by wanga on 2017/6/3.
  */

class MemTestModule(val hwConfig:HardwareConfig, val actualBW:Int) extends Module{
  val amem=Module(new AggregateMem(hwConfig,actualBW))
  val io=IO(new Bundle{
    val test=Input(Bool())
    val addr=Input(UInt(hwConfig.memAddrWidth.W))
    val in=Input(Bits(amem.lineWidth.W))
    val out=Output(Bits(amem.lineWidth.W))
    val wen=Input(Bool())
    val ren=Input(Bool())
    val testout=Output(Bits(amem.lineWidth.W))
  })
  amem.io.addr:=io.addr
  amem.io.waddr:=io.addr
  amem.io.in:=io.in
  io.out:=amem.io.out
  amem.io.wen:=io.wen
  amem.io.ren:=io.ren

  val reg=RegInit(0.U(4.W))

  val outreg=RegInit(0.U(amem.lineWidth.W))
  io.testout:=outreg

  when(io.test && reg===0.U){
    amem.io.addr:=io.addr
    reg:=1.U
  }
  when(io.test && reg===1.U){
    outreg:=amem.io.out
    reg:=2.U
  }
  when(io.test && reg===2.U){
    outreg:=amem.io.out
    reg:=0.U
  }
}


class MemTest(amem:MemTestModule) extends PeekPokeTester(amem){
  //input N entries
  val N=256
  val data=Array.fill(N){
    Array.fill(amem.hwConfig.memCnt){
      Random.nextInt(1<<amem.actualBW)
    }.foldLeft(BigInt(0)){case (acc, v)=>(acc<<amem.actualBW)+v}
  }

  poke(amem.io.wen,true)
  poke(amem.io.ren, false)
  poke(amem.io.test, false)

  for(i<-0 until N){
    poke(amem.io.addr, i)
    poke(amem.io.in, data(i))

    step(1)
  }

  poke(amem.io.wen, false)
  poke(amem.io.ren, true)

  for(i<-0 until N){
    poke(amem.io.addr, i)
    step(1)
    expect(amem.io.out, data(i))
  }

  poke(amem.io.test, true)
  poke(amem.io.addr, 3)
  step(1)

  expect(amem.io.testout, 0)
  step(1)

  expect(amem.io.testout, data(3))
}

