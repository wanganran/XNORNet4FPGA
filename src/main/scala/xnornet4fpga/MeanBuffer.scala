package xnornet4fpga

import chisel3._
import chisel3.util._

/**
  * Created by wanganran on 5/29/17.
  */
class MeanBuffer(bitw:Int, n:Int) extends Module{
  val io=IO(new Bundle{
    val in=Input(Vec(n, SInt(bitw.W)))
    val cnt=Input(SInt(bitw.W))
    val en=Input(Bool())
    val reset=Input(Bool())
    val out=Input(SInt(bitw.W))
  })

  def abs(x:SInt)=Mux(x>0.S, x, 0.S-x)

  val acc=RegInit(0.S(bitw.W))
  acc:=acc+io.in.map(abs).sum
  when(io.en){
    io.out:=acc/io.cnt
  }
}
