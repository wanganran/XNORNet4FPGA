package xnornet4fpga

import chisel3._
import chisel3.util._

/**
  * Created by wanganran on 5/28/17.
  */
class BinaryBuffer(totalWidth:Int, rWidth:Int, wWidth:Int) extends Module{
  val io=IO(new Bundle{
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

  val wPos=RegInit(0.U(log2Ceil(wCnt).W))
  val rPos=RegInit(0.U(log2Ceil(rCnt).W))

  val mem=Vec(wCnt, Reg(Bits(wWidth.W)))
  val catMem=Vec((0 until wCnt by wPerR) map {i=>Cat((0 until wPerR) map {j=>mem(i+j)})})

  when(io.push){
    mem(wPos):=io.in
    wPos:=wPos+1.U
  }

  when(io.pop){
    rPos:=rPos+1.U
  }

  io.out:=catMem(rPos)
}
