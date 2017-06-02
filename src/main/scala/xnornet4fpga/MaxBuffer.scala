package xnornet4fpga

import chisel3._
import chisel3.util._

/**
  * Created by wanganran on 5/30/17.
  */
class MaxBuffer(bitw:Int, bitr:Int, n:Int) extends Module {
  val io=IO(new Bundle{
    val reset=Input(Bool())
    val en=Input(Bool())
    val in=Input(Vec(n, SInt(bitw.W)))
    val offset=Input(UInt(bitr.W))
    val out=Output(UInt(bitr.W))
  })
  case class WIndex(idx:UInt, content:SInt) extends Bundle
  val data=Reg(WIndex(0.U,SInt(bitw.W)))

  io.out:=data.idx
  def reduce(in:IndexedSeq[WIndex]):WIndex={
    if(in.length==1)in(0)
    else {
      assert(in.length % 2 == 0)
      reduce((0 until in.length by 2).map {
        i => Mux(in(i).content > in(i + 1).content, in(i), in(i + 1))
      })
    }
  }
  val m=reduce(io.in.zipWithIndex.map{case (x, idx)=>WIndex(idx.U+io.offset, x)})
  when(io.en) {
    when(!(io.reset)) {
      data := Mux(data.content > m.content, data, m)
    } otherwise {
      data:=m
    }
  }
}
