package xnornet4fpga

import chisel3._
import chisel3.util._

//Simplified topology:
//input -> layer_i(width) -> ... -> result

/**
  * Created by wanganran on 5/28/17.
  * Clock   Desc              Action
  * Pre-0   Mem is ready
  *
  * === Init input ===
  * // x=inputWidth/aggregateMemWidth; T=aggregateMemWidth/XNORFanout
  * x times:
  * -1      read input        (inputAddr, True)->AggregateMem; INPUT->inputSel; True->inputBufferPush; False->inputBufferPop
  *                           if not the first time: AggregateMem[i]->input
  * 0:T-1   save to buffer    output of AggregateMem[i] -> input;
  *
  * === Start layer #1 ===
  * //k=FeatureCnt/XNORFanout; d=inputSize/XNORWidth
  * -1      unset input       False->inputBufferPush
  * -1      update memSel     WEIGHT->memSel
  * -1      update weight     (featureAddr, True)->AggregateMem;
  * -1      reset acc         True->accReset
  * -1      update accSel     0->accSel
  * 0       XNOR,bit sum,etc
  * 0       update weight     (featureAddr++, True)->AggregateMem;
  * 0       update accSel     1->accSel
  * ...
  * T-1     shift input       True->inputBufferPop
  * T-1     reset featureAddr reset(featureAddr)
  * ...
  * last iteration: start reading bn params
  * //bnCnt=memLineWidth/bnParamWidth/2, c=XNORFanout/bnCnt
  * -1      update bnBuffer   (bnAddr, True)->AggregateMem;
  * -1      update memSel     BN0->memSel
  * -1      update accSel     0->accSel
  * -1      disable acc       False->accEn
  * state   update dest       BN{state+1}->memSel
  * last_st push to buffer    True->inputBufferPush
  * last_st switch accSel     1->accSel
  *
  * lastacc start 2nd layer
  *
  * if last layer:
  * -1      max               True->maxEn
  *
  */
class IglooScheduler(hwConfig: HardwareConfig, topo:NNTopology) extends Module {
  val TEST=true
  val io = IO(new Bundle {
    val en = Input(Bool())
    val inputOffset = Input(UInt(hwConfig.memAddrWidth.W))
    val memOffset = Input(UInt(hwConfig.memAddrWidth.W))
    val finished = Output(Bool())
    val result = Output(UInt(hwConfig.resultWidth.W))



    //for input mem
    val memWen=Input(Bool())
    val memWAddr=Input(UInt(hwConfig.memAddrWidth.W))
    val memIn=Input(UInt(128.W)) //TODO: parameterize

    //for test
    val state=Output(UInt(16.W))
  })

  //=== UTILITY ===

  //round k

  //auto symbol->state
  var symbols = Array[Symbol]()

  def S(x: Symbol): UInt = {
    val idx = symbols.indexOf(x)
    if (idx >= 0) idx.U(8.W)
    else {
      symbols = symbols :+ x
      S(x)
    }
  }

  //=== FIXED CONFIGURATION ===

  val hw = Module(new XNORNetInference(hwConfig))

  hw.io.memWen:=io.memWen
  hw.io.memIn:=io.memIn
  hw.io.memWAddr:=io.memWAddr

  //assign result
  io.result:=hw.io.result

  val layerParams=Module(new LayerParamShifter(hwConfig, topo))

  hw.io.maxEn:=layerParams.io.lastLayer

  //state
  val state=RegInit(S('init))
  val substate=RegInit(0.U(16.W))
  if(TEST)io.state:=state

  def IS(x:Symbol, i:Int)=state===S(x) && substate===i.U
  def IS(x:Symbol)=state===S(x)

  def SS(x:Symbol, i:Int=0){state:=S(x); substate:=i.U}
  def SS(i:Int){substate:=i.U}
  def SS(){substate:=substate+1.U}

  def set(x:Bits){x:=true.B}
  def unset(x:Bits){x:=false.B}

  //split input into XNORFanout
  val inputBuffer = hw.io.memOut

  //max offset to the max module
  val maxOffsetReg=Reg(UInt(hwConfig.resultWidth.W))
  hw.io.maxOffset:=maxOffsetReg

  //mem offset reg
  val memOffset=Reg(UInt(hwConfig.memAddrWidth.W))

  //next acc is always related to substate
  val acc=substate%layerParams.io.currentAccWidth
  hw.io.accSel:=acc

  io.finished:=IS('finished)

  //=== STATE MACHINE ===

  when(io.en) {
    val initialInputRound =topo(0)._1/hw.mem.lineWidth
    when(IS('init)) {
      hw.io.memAddr := io.inputOffset

      memOffset:=io.memOffset

      unset(hw.io.inputBufferPush)
      unset(hw.io.inputBufferPop)
      set(hw.io.inputBufferReset)
      hw.io.input:=inputBuffer

      //assume MLP
      SS('waitMem)
    }
    when(IS('waitMem)){
      //next tick push input
      unset(hw.io.inputBufferReset)
      set(hw.io.inputPush)
      hw.io.memAddr:=(io.inputOffset+1.U)
      SS('readInput)
    }
    when(IS('readInput)) {
      //first inputBuffer ready

      when(substate < (initialInputRound - 2).U) {
        //set next input
        hw.io.memAddr:=(io.inputOffset+2.U+substate)
        SS()
      }.elsewhen(substate === (initialInputRound - 2).U) {
        hw.io.memAddr := io.memOffset
        SS('xnorPrepare)
      }
    }
    when(IS('xnorPrepare)) {
      //unset fastpush
      unset(hw.io.inputPush)
      //prepare for layer 1
      set(hw.io.accEn)
      //next tick it will reset to the input
      set(hw.io.accReset)
      hw.io.accSel := 0.U
      //pre-update mem addr
      hw.io.memAddr:=memOffset //TODO: remove this
      memOffset:=memOffset+1.U
      SS('xnor)
      //next tick it will do the XNOR
    }
    when(IS('xnor)){
      //this time: input and weight are ready, acc enabled,
      //stop shift layer
      unset(layerParams.io.shift)
      //stop reset acc
      unset(hw.io.accReset)
      //stop update last mean
      unset(hw.io.meanUpdate)
      //always switch weight
      memOffset:=memOffset+1.U

      when(acc===layerParams.io.currentAccWidth-2.U){
        //switch input
        set(hw.io.inputBufferPop)
      }.elsewhen(acc===layerParams.io.currentAccWidth-1.U){
        unset(hw.io.inputBufferPop)
      }

      when(substate<layerParams.io.currentTotalRound-1.U){
        // every step but last
        SS()
      }.elsewhen(substate===layerParams.io.currentTotalRound-1.U){
        //update to BN
        //next tick bn result should be ready, push result back to buffer
        set(hw.io.inputBufferPush)
        SS('bn)
        //clear max
        set(hw.io.maxReset)
        //clear mean
        set(hw.io.meanReset)

        //set max offset
        maxOffsetReg:=0.U
        //stop acc
        unset(hw.io.accEn)
      }
    }
    when(IS('bn)){
      //this time: bn mem is ready, result is ready, pushing
      //continue read next mem
      memOffset:=memOffset+1.U
      //unset clear
      unset(hw.io.maxReset)
      unset(hw.io.meanReset)
      //add max offset
      maxOffsetReg:=maxOffsetReg+hwConfig.XNORFanout.U
      SS()
      when(substate===layerParams.io.currentAccWidth -1.U){
        //last tick,
        //if last layer return result
        when(layerParams.io.lastLayer) {
          //stop max
          unset(hw.io.maxEn)
          SS('finished)
        } otherwise{
          //start a new XNOR
          //add layer
          set(layerParams.io.shift)
          //update mean
          set(hw.io.meanUpdate)

          //not push data any more
          unset(hw.io.inputBufferPush)
          //reset acc values
          set(hw.io.accReset)
          //re-enable acc
          set(hw.io.accEn)
          SS('xnor)
        }
      }
    }
  }
}
