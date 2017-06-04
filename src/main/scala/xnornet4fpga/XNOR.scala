package xnornet4fpga

import chisel3._
import chisel3.core.{Bundle, Module}
import chisel3.util._
/**
  * One k-bit array XNOR d k-bit arrays
  * output d k-bit arrays
  * Created by wanganran on 5/3/17.
  */
class XNOR(k:Int, d:Int) extends Module {
  val io = IO(new Bundle {
    val in1 = Input(Bits(k.W))
    val in2 = Input(Vec(d, Bits(k.W)))
    val out = Output(Vec(d, Bits(k.W)))
  })
  for (i <- 0 until d) {
    io.out(i) := ~(io.in1 ^ io.in2(i))
  }
}
