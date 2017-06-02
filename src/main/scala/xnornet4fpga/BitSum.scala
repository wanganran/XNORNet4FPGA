package xnornet4fpga

import chisel3._
import chisel3.util._
/**
  * Created by wanganran on 5/3/17.
  */
case class BitSum(w:Int, bitc:Int){
  private class LookupTable(max:Int, inBitw:Int, outBitw:Int, map:Int=>Int) extends Module{
    val io=IO(new Bundle {
      val in = Input(Bits(inBitw.W))
      val out = Output(SInt(outBitw.W))
    })
    val lut=(0 until max).map(map).map(_.S(outBitw.W))
    val lutReg=Reg(Vec(lut))
    io.out:=lutReg(io.in)
  }
  val LUT_WIDTH=4
  val lut_size=(1<<LUT_WIDTH)
  def generate(in:Bits)= {
    (0 until w by LUT_WIDTH).toArray.map(x => in(x, x + LUT_WIDTH - 1)).foldLeft(0.S(bitc.W)) {
      (acc, v) =>
        val lookupTable = Module(new LookupTable(lut_size, LUT_WIDTH, bitc, x => {
          def acc(x: Int): Int = if (x == 0) 0 else x % 2 + acc(x >> 1)
          acc(x) * 2 - lut_size
        }))
        lookupTable.io.in := v
        acc + lookupTable.io.out
    }
  }
}
