package xnornet4fpga

import chisel3._
import chisel3.util._

/**
  * Created by wanganran on 5/28/17.
  */
class AggregateMem(hwConfig:HardwareConfig, val actualBitWidthPerMem:Int) extends Module {
  val mems=Array.fill(hwConfig.memCnt){
    Module(new Memo(hwConfig.memLineWidth, hwConfig.memAddrWidth, hwConfig.memSize))
  }
  for(i<-0 until mems.length-1){
    assert(mems(i).lineWidth==mems(i+1).lineWidth)
    assert(mems(i).addrWidth==mems(i+1).addrWidth)
    assert(mems(i).size==mems(i+1).size)
  }
  assert(actualBitWidthPerMem<mems(0).lineWidth)

  val lineWidth=actualBitWidthPerMem*mems.length
  val addrWidth=mems(0).addrWidth
  val size=mems(0).size

  val io=IO(new Bundle{
    val addr=Input(UInt(addrWidth.W))
    val waddr=Input(UInt(addrWidth.W))
    val in=Input(Bits(lineWidth.W))
    val out=Output(Bits(lineWidth.W))
    val wen=Input(Bool())
    val ren=Input(Bool())
  })

  for(i<-0 until mems.length){
    mems(i).io.ren:=io.ren
    mems(i).io.wen:=io.wen
    mems(i).io.rdAddr:=io.addr
    mems(i).io.wrAddr:=io.waddr
  }
  for(i<-0 until mems.length) {
    mems(i).io.wrData := Cat(0.U((hwConfig.memLineWidth-actualBitWidthPerMem).W), io.in((i + 1) * actualBitWidthPerMem - 1, i * actualBitWidthPerMem))
  }
  io.out:=Cat((mems.length-1 to 0 by -1) map {mems(_).io.rdData(actualBitWidthPerMem - 1, 0)})

}
