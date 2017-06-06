package xnornet4fpga

import chisel3._
import chisel3.util._

/**
  * Created by wanganran on 5/29/17.
  */
class MeanBuffer(bitw:Int, n:Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(Vec(n, SInt(bitw.W)))
    val cntInverse65536 = Input(UInt(16.W))
    val reset = Input(Bool())
    val out = Output(UInt(bitw.W))
  })

  def abs(x: SInt) = Mux(x > 0.S, x, 0.S - x).asUInt()

  val acc = Reg(UInt(bitw.W))
  val result=Reg(UInt(bitw.W))
  io.out:=result

  val absSum=ArraySum(n).generate(io.in map abs)
  when(!(io.reset)) {
    //for(i<-0 until 4)
    //  printf("Mean "+i+" Update! %d %d %d\n", io.in(i), acc, io.cntInverse65536)
    acc := acc + absSum
    result := ((acc+absSum) * io.cntInverse65536)>>16
  } otherwise {
    //for(i<-0 until 4)
    //  printf("Mean "+i+" Reset! %d\n", io.in(i))
    acc := absSum
    result := (absSum * io.cntInverse65536)>>16
  }
}
