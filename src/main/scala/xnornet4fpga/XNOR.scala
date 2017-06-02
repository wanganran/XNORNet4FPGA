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
  val io=IO(new Bundle{
    val in1=Input(Bits(k.W))
    val in2=Input(Vec(d, Bits(k.W)))
    val out=Output(Vec(d, Bits(k.W)))
    val en=Input(Bool())
  })
  when(io.en) {
    for (i <- 0 until d) {
      for (j <- 0 until k) {
        io.out(i)(j) := io.in1(j) ^ io.in2(i)(j)
      }
    }
  }
}
