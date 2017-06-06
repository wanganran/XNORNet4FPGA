package xnornet4fpga

import chisel3._
import chisel3.util._

/**
  * Created by wanga on 2017/6/3.
  */
class DelayedOutput(w:Int, default:Int) extends Module {
  val io=IO(new Bundle{
    val reset=Input(Bool())
    val input=Input(Bits(w.W))
    val update=Input(Bool())
    val output=Output(Bits(w.W))
  })

  val reg=Reg(UInt(w.W))
  io.output:=reg
  when(io.reset) {
    reg:=default.U(w.W)
    printf("Delay reset! %d\n", reg)
  }.elsewhen(io.update){
    printf("Delay Update! %d\n", io.input)
    reg:=io.input
  }
}
