// See LICENSE.txt for license details.
package xnornet4fpga

import chisel3._
import chisel3.util._

// Problem:
//
// Count incoming trues
// (increase counter every clock if 'in' is asserted)
//
class Accumulator(bitw:Int, n:Int) extends Module {
  val io = IO(new Bundle {
    val in  = Input(SInt(bitw.W))
    val out = Output(SInt(bitw.W))
    val sel=Input(UInt(log2Ceil(n).W))
    val en=Input(Bool())
    val reset=Input(Bool())
  })
  val accumulator = Reg(Vec(n, SInt(bitw.W)))
  val acc = accumulator(io.sel)

  when(io.en && !(io.reset)) {
    acc := acc + io.in
  }

  io.out := acc

  when(io.en && io.reset){
    for(i<-0 until n)
      accumulator(i):=Mux(i.U===io.sel, io.in, 0.S)
  }
}
