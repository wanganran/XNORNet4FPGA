package xnornet4fpga

import chisel3._
import chisel3.util._

/**
  * Created by wanganran on 5/28/17.
  */
class BinaryBuffer(totalWidth:Int, rWidth:Int, wWidth:Int) extends Module{
  val io=IO(new Bundle{
    val reset=Input(Bool())
    val in=Input(Bits(wWidth.W))
    val push=Input(Bool())
    val out=Output(Bits(rWidth.W))
    val pop=Input(Bool())
  })

  //assume wWidth is always no bigger than rWidth
  assert(rWidth%wWidth==0)
  val wCnt=totalWidth/wWidth
  val rCnt=totalWidth/rWidth
  val wPerR=wCnt/rCnt

  val wPos=Reg(UInt(log2Ceil(wCnt).W))
  val rPos=Reg(UInt(log2Ceil(rCnt).W))

  val mem=Reg(Vec(wCnt, Bits(wWidth.W)))
  val catMem=Vec((0 until wCnt by wPerR) map {i=>Cat((0 until wPerR) map {j=>mem(i+j)})})

  when(io.push){
    when(!(io.reset)) {
      mem(wPos) := io.in
      wPos := wPos + 1.U
    } otherwise {
      mem(0):=io.in
      wPos:=1.U
      rPos:=0.U
    }
  }

  when(io.pop){
    when(!(io.reset)) {
      rPos := rPos + 1.U
    } otherwise{
      rPos:=1.U
      wPos:=0.U
    }
  }

  when(io.reset && !(io.pop) && !(io.push)){
    wPos:=0.U
    rPos:=0.U
  }

  io.out:=catMem(rPos)
}
