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

  val idxReg=Reg(UInt(bitr.W))
  val dataReg=Reg(SInt(bitw.W))

  io.out:=idxReg
  def reduce(in:IndexedSeq[(UInt, SInt)]):(UInt, SInt)={
    if(in.length==1)in(0)
    else {
      assert(in.length % 2 == 0)
      reduce((0 until in.length by 2).map { i =>
        val cmp = in(i)._2 > in(i + 1)._2
        (Mux(cmp, in(i)._1, in(i + 1)._1), Mux(cmp, in(i)._2, in(i + 1)._2))
      })
    }
  }
  val m=reduce(io.in.zipWithIndex.map{case (x, i)=>(i.U+io.offset,x)})

  val cmp2=dataReg > m._2

  when(io.en) {
    when(!(io.reset)) {
      dataReg := Mux(cmp2, dataReg, m._2)
      idxReg :=Mux(cmp2, idxReg, m._1)
    } otherwise {
      dataReg := m._2
      idxReg:=m._1
    }
  }
}
