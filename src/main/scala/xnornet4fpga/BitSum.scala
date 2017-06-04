package xnornet4fpga

import chisel3._
import chisel3.util._
/**
  * Created by wanganran on 5/3/17.
  */
case class BitSum(w:Int, bitc:Int){
  private case class LookupTable(max:Int, outBitw:Int, map:Int=>Int) {
    val lut = (0 until max).map(map)
    def generate()= Vec(lut.map(x=>RegInit(x.S(outBitw.W))))
  }

  private def generateS(s:Seq[SInt]):SInt=s.length match{
    case 1 => s.head
    case x if x%2==0 =>
      generateS((0 until s.length by 2) map { i =>
        s(i) + s(i + 1)
      })
    case x if x%2==1 => s.head+generateS(s.tail)
  }

  val LUT_WIDTH=4
  val lut_size=(1<<LUT_WIDTH)
  def generate(in:Bits)= {
    val lt=LookupTable(lut_size, bitc, x => {
          def acc(x: Int): Int = if (x == 0) 0 else x % 2 + acc(x >> 1)
          acc(x) * 2 - LUT_WIDTH
        })

    generateS((0 until w by LUT_WIDTH).map(x => in(x + LUT_WIDTH - 1, x)).map(lt.generate()(_)))
  }
}
