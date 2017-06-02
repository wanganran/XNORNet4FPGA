package xnornet4fpga

import chisel3._
import chisel3.util._

/**
  * Created by wanganran on 5/30/17.
  */

  class Mux4 extends Module {
  val io = IO(new Bundle {
    val in0 = Input(UInt(1.W))
    val in1 = Input(UInt(1.W))
    val in2 = Input(UInt(1.W))
    val in3 = Input(UInt(1.W))
    val sel = Input(UInt(2.W))
    val out = Output(UInt(1.W))
  })

  val m0 = Mux(
    io.sel(0),
    io.in0,
    io.in1
  )

  val m1 = Mux(
    io.sel(0),
    io.in2,
    io.in3
  )

  val m2 = Mux(
    io.sel(1),
    m0,
    m1
  )
  io.out := m2
}
