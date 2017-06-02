package xnornet4fpga

import chisel3._
import chisel3.util._

/**
  * Created by wanganran on 5/28/17.
  */
class AggregateMem(mems:Array[Memo]) extends Module {
  for(i<-0 until mems.length-1){
    assert(mems(i).lineWidth==mems(i+1).lineWidth)
    assert(mems(i).addrWidth==mems(i+1).addrWidth)
    assert(mems(i).size==mems(i+1).size)
  }

  val lineWidth=mems(0).lineWidth*mems.length
  val addrWidth=mems(0).addrWidth
  val size=mems(0).size

  val io=IO(new Bundle{
    val addr=Input(UInt(addrWidth.W))
    val out=Output(Bits(lineWidth.W))
  })

  for(i<-0 until mems.length){
    mems(i).io.ren:=true.B
    mems(i).io.wen:=false.B

    mems(i).io.rdAddr:=io.addr
  }
  for(i<-0 until mems.length)
    io.out((i+1)*mems(i).lineWidth-1,i*mems(0).lineWidth):=mems(i).io.rdData
}
