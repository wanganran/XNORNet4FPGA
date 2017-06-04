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
  def io = IO(new Bundle {
    val en = Input(Bool())
    val inputOffset = Input(UInt(hwConfig.memAddrWidth.W))
    val memOffset = Input(UInt(hwConfig.memAddrWidth.W))
    val finished = Output(Bool())
    val result = Output(UInt(hwConfig.resultWidth.W))
  })

  //round k
  def round(x: Int, k: Int) = (x - 1) / k * k + k
  def maxPower2(x:Int)=(1<<log2Floor(x))

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

  val mem = Module(new AggregateMem(hwConfig, maxPower2(hwConfig.memLineWidth)))

  val hw = Module(new XNORNetInference(hwConfig, mem))
  //assign result
  io.result:=hw.io.result

  val currentLayer = Reg(0.U(4.W))
  val currentFeatureCnt = MuxLookup(currentLayer, 0.U,
    (0 until topo.getTotalLayers()) map {
      i => i.U -> round(topo(i)._2, hwConfig.XNORFanout).U
    })
  val accWidth = currentFeatureCnt / hwConfig.XNORFanout.U
  val inputRound = MuxLookup(currentLayer, 0.U,
    (0 until topo.getTotalLayers()) map {
      i => i.U -> (round(topo(i)._1, hwConfig.XNORBitWidth)/hwConfig.XNORBitWidth).U
    })
  val currentTotalRound=accWidth*inputRound

  //if last round, switch to max
  val lastLayer=Mux(currentLayer===(topo.getTotalLayers()-1).U, true.B, false.B)
  hw.io.maxEn:=lastLayer

  val state=RegInit(S('init))
  val substate=RegInit(0.U(16.W))

  def IS(x:Symbol, i:Int)=state===S(x) && substate===i.U
  def IS(x:Symbol)=state===S(x)

  def SS(x:Symbol, i:Int=0){state:=S(x); substate:=i.U}
  def SS(i:Int){substate:=i.U}
  def SS(){substate:=substate+1.U}

  def set(x:Bits){x:=true.B}
  def unset(x:Bits){x:=false.B}

  val inputSize=topo.getParameterSizeType(0).map{case (s, _)=>s}.sum
  val x=inputSize/mem.lineWidth

  val inputBuffer = mem.io.out(mem.lineWidth - 1, 0).asTypeOf(
    Vec(mem.lineWidth/hwConfig.XNORFanout,Bits(hwConfig.XNORFanout.W)))

  val maxOffsetReg=Reg(UInt(hwConfig.resultWidth.W))
  hw.io.maxOffset:=maxOffsetReg

  when(io.en) {
    when(IS('init)) {
      hw.io.memSel := XNORNetInference.MEMSEL_XNOR
      hw.io.inputSel := XNORNetInference.INPUTSEL_INPUT
      mem.io.addr := io.inputOffset
      currentLayer:=0.U
      //assume MLP
      SS('wait)
    }
    when(IS('wait)){
      SS('readInput)
    }
    when(IS('readInput)) {
      //inputBuffer ready
      set(hw.io.inputBufferPush)
      unset(hw.io.inputBufferPop)

      val inputBufferLen = mem.lineWidth / hwConfig.XNORFanout
      when(substate < (inputBufferLen - 1).U) {
        hw.io.input := inputBuffer((inputBufferLen - 1).U - substate)
        SS()
      }.elsewhen(substate === (inputBufferLen - 1).U) {
        hw.io.input := inputBuffer((inputBufferLen - 1).U - substate)
        mem.io.addr := io.memOffset
        SS('xnorPrepare)
      }
    }
    when(IS('xnorPrepare)) {
      //prepare for layer 1
      unset(hw.io.inputBufferPush)
      hw.io.inputSel := XNORNetInference.INPUTSEL_FEEDBACK
      set(hw.io.accEn)
      //next tick it will reset to the input
      set(hw.io.accReset)
      hw.io.accSel := 0.U
      //pre-update mem addr
      mem.io.addr:=io.memOffset+1.U
      SS('xnor)
      //next tick it will do the XNOR
    }
    when(IS('xnor)){
      //this time: input and weight are ready, acc enabled,
      // acc result is going to be ready
      //select next acc
      hw.io.accSel:=(substate+1.U)%accWidth
      //always switch weight
      mem.io.addr:=io.memOffset+substate+2.U
      when(substate<currentTotalRound-1.U){
        // every step but last
        SS()
      }.elsewhen(substate===currentTotalRound-1.U){
        //update to BN
        hw.io.memSel:=XNORNetInference.MEMSEL_BN
        //next tick bn result should be ready, push result back to buffer
        set(hw.io.inputBufferPush)
        SS('bn)
        //clear max
        set(hw.io.maxClear)
        //set max offset
        maxOffsetReg:=0.U
        //stop acc
        unset(hw.io.accEn)
      }
      when(substate%accWidth===accWidth-2.U){
        //switch input
        set(hw.io.inputBufferPop)
      }.elsewhen(substate%accWidth===accWidth-1.U){
        unset(hw.io.inputBufferPop)
      }
    }
    when(IS('bn)){
      when(substate===accWidth-1.U){
        //last tick,
        //if last layer return result
        when(lastLayer) {
          set(io.finished)
          //stop max
          unset(hw.io.maxEn)
          SS('finished)
        } otherwise{
          //start a new XNOR
          //add layer
          currentLayer:=currentLayer+1.U
          //not push data any more
          unset(hw.io.inputBufferPush)
          //reset acc values
          set(hw.io.accReset)
          //re-enable acc
          set(hw.io.accEn)
          //set accSel back to 0
          hw.io.accSel := 0.U
          SS('xnor)
        }
      }
      //this time: bn mem is ready, result is ready, pushing
      //select next acc
      hw.io.accSel:=substate+1.U
      //continue read next mem
      mem.io.addr:=io.memOffset+substate+currentTotalRound+2.U
      //unset clear
      unset(hw.io.maxClear)
      //add max offset
      maxOffsetReg:=maxOffsetReg+hwConfig.XNORFanout.U
      SS()
    }
  }
}
