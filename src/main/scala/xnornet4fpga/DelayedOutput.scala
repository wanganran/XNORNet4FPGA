package xnornet4fpga

import chisel3._
import chisel3.util._

/**
  * Created by wanga on 2017/6/3.
  */
class DelayedOutput(w:Int, default:Int) extends Module {
  val io=IO(new Bundle{
    val input=Input(Bits(w.W))
    val update=Input(Bool())
    val output=Output(Bits(w.W))
  })

  val reg=RegInit(default.U(w.W))
  io.output:=reg
  when(io.update){
    reg:=io.input
  }
}
