package xnornet4fpga

import chisel3._

class Memo(val lineWidth:Int, val addrWidth:Int, val size:Int) extends Module {
  val io = IO(new Bundle {
    val wen     = Input(Bool())
    val wrAddr  = Input(UInt(addrWidth.W))
    val wrData  = Input(Bits(lineWidth.W))
    val ren     = Input(Bool())
    val rdAddr  = Input(UInt(addrWidth.W))
    val rdData  = Output(Bits(lineWidth.W))
  })

  val mem = Mem(size, UInt(lineWidth.W))

  // write
  when (io.wen) { mem(io.wrAddr) := io.wrData }
  
  // read
  io.rdData := 0.U
  when (io.ren) { io.rdData := mem(io.rdAddr) }

}
