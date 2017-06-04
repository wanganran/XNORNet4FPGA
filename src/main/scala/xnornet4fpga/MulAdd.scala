package xnornet4fpga

import chisel3._
import chisel3.util._

/**
  * Created by wanganran on 5/28/17.
  */
class MulAdd(inBitw:Int, outBitw:Int) extends Module{
  val io=IO(new Bundle{
    val a=Input(SInt(inBitw.W))
    val b=Input(SInt(inBitw.W))
    val m=Input(UInt(outBitw.W))
    val c=Input(SInt(inBitw.W))
    val r=Output(SInt(outBitw.W))
  })
  io.r:=io.a*io.b*io.m+io.c
}
