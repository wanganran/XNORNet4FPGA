package xnornet4fpga
import chisel3._
import chisel3.util._

/**
  * Created by wanga on 2017/6/3.
  */
case class ArraySum(n:Int) {
  def generate(s:Seq[UInt]):UInt=s.length match{
    case 1 => s(0)
    case x if x%2==0 =>
      generate((0 until s.length by 2) map { i =>
        s(i) + s(i + 1)
      })
    case x if x%2==1 => s.head+generate(s.tail)
  }
}
