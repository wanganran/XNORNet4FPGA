package xnornet4fpga

import chisel3._
import chisel3.util._


/**
  * Created by wanganran on 5/30/17.
  */
class MemFO(aggregateMem: AggregateMem) extends Module {
  val io=IO(new Bundle{
    val hold=Input(Bool())
    val reset=Input(Bool())
    val resetAddr=Input(UInt(aggregateMem.addrWidth.W))
    val output=Output(Bits(aggregateMem.lineWidth.W))
  })
  val currentAddr=Reg(UInt(aggregateMem.addrWidth.W))
  aggregateMem.io.addr:=currentAddr
  io.output:=aggregateMem.io.out
  when(io.reset){
    currentAddr:=io.resetAddr
  }
  when(!(io.hold)){
    currentAddr:=currentAddr+1.U
  }
}
