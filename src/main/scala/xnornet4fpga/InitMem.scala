package xnornet4fpga

import chisel3._
import chisel3.util._

/**
  * Created by wanganran on 5/28/17.
  */
class InitMem (mems:Array[Memo], memSelWidth:Int, bitsPerClk:Int) extends Module{
  for(i<-0 until mems.length-1){
    assert(mems(i).lineWidth==mems(i+1).lineWidth)
    assert(mems(i).addrWidth==mems(i+1).addrWidth)
    assert(mems(i).size==mems(i+1).size)
  }
  assert(mems(0).lineWidth%bitsPerClk==0)


  /**
    * for microcontroller:
    *
    * set en
    *
    * set fill memId
    * fill addr
    * loop memLW/LW times:
    *   fill data
    *   set ready
    *   unset ready
    *
    * finally,
    * unset en
    */

  val io=IO(new Bundle(){
    val ready=Input(Bool())
    val memId=Input(UInt(memSelWidth.W))
    val addr=Input(UInt(mems(0).addrWidth.W))
    val data=Input(Bits(bitsPerClk.W))
    val en=Input(Bool())
    val finish=Output(Bool())
  })

  val lineWidth=mems(0).lineWidth
  val state=RegInit(0.U((log2Ceil(mems(0).lineWidth)+1).W))
  val data=Reg(Bits(mems(0).lineWidth.W))

  when(io.en && io.ready){
    for(i<-0 until lineWidth-memSelWidth by memSelWidth) {
      when(state === i.U) {
        data(i,i+memSelWidth-1):=io.data
        state:=(i+2).U
      }
    }
    when(state===lineWidth.U){
      data(lineWidth-2, lineWidth-1):=io.data
      for(i<-0 until mems.length){
        when(io.memId===i.U){
          mems(i).io.wrAddr:=io.addr
          mems(i).io.wrData:=data
          state:=0.U
        }
      }
    }
  }

  when(!io.en && state === mems(0).lineWidth.U){
    io.finish:=true.B
  }

  when(!io.finish){
    mems.foreach{m=>
      m.io.ren:=false.B()
      m.io.wen:=true.B()
    }
  }

}
