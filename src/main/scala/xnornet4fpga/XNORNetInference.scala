package xnornet4fpga

import java.io.FileOutputStream

import chisel3._
import chisel3.util._


object XNORNetInference{

  val MEMSEL_XNOR=true.B
  val MEMSEL_BN=false.B

  val INPUTSEL_INPUT=true.B
  val INPUTSEL_FEEDBACK=false.B

}
/**
  * Created by wanganran on 5/28/17.
  */
class XNORNetInference(hardwareConfig: HardwareConfig) extends Module {
  def maxPower2(x:Int)=(1<<log2Floor(x))
  val mem=Module(new AggregateMem(hardwareConfig, maxPower2(hardwareConfig.memLineWidth)))
  val maxAccWidth=hardwareConfig.maxFeatures/hardwareConfig.XNORFanout //max # rounds of computations
  val bnCnt=mem.lineWidth/(hardwareConfig.bnParamWidth*2) //# of bn param pairs loaded each clock
  val bnFanout=hardwareConfig.XNORFanout/bnCnt //TODO:now only support 1

  val io=IO(new Bundle{
    val input=Input(Bits(hardwareConfig.XNORFanout.W))
    //currently not used
    val bnBufferSel=Input(Bits(log2Ceil(bnFanout).W))
    val inputSel=Input(Bool())
    val inputBufferPush=Input(Bool())
    val inputBufferPop=Input(Bool())
    val inputBufferReset=Input(Bool())
    val memSel=Input(Bool())
    val memAddr=Input(UInt(mem.addrWidth.W))
    val memOut=Output(Bits(mem.lineWidth.W))
    val memRen=Input(Bool())
    val memWen=Input(Bool())
    val memIn=Input(Bits(mem.lineWidth.W))
    val memWAddr=Input(UInt(mem.addrWidth.W))
    val accEn=Input(Bool())
    val accSel=Input(UInt(log2Ceil(maxAccWidth).W))
    val accReset=Input(Bool())
    val maxReset=Input(Bool())
    val maxEn=Input(Bool())
    val maxOffset=Input(UInt(hardwareConfig.resultWidth.W))
    val meanReset=Input(Bool())
    val featureNumInverse65536=Input(UInt(16.W))
    val meanUpdate=Input(Bool())

    val result=Output(UInt(hardwareConfig.resultWidth.W))
  })

  mem.io.addr:=io.memAddr
  io.memOut:=mem.io.out
  mem.io.ren:=io.memRen
  mem.io.wen:=io.memWen
  mem.io.in:=io.memIn

  val inputWire=Wire(Bits(hardwareConfig.XNORFanout.W)) //connected by the output signs

  val binaryBuffer=Module(new BinaryBuffer(
    Math.max(hardwareConfig.maxFeatures, hardwareConfig.maxInputWidth),  //storage size in bits
    hardwareConfig.XNORBitWidth,  //output width
    hardwareConfig.XNORFanout))   //input width
  binaryBuffer.io.in:=Mux(io.inputSel, io.input, inputWire)
  binaryBuffer.io.push:=io.inputBufferPush
  binaryBuffer.io.pop:=io.inputBufferPop
  binaryBuffer.io.reset:=io.inputBufferReset

  val xnor=Module(new XNOR(
    hardwareConfig.XNORBitWidth, //input width
    hardwareConfig.XNORFanout))  //how many vectors are XNORed parallel
  xnor.io.in1:=binaryBuffer.io.out

  val memOut=mem.io.out

  xnor.io.in2:=Mux(
    io.memSel,
    memOut.asTypeOf(Vec(hardwareConfig.XNORFanout, UInt(hardwareConfig.XNORBitWidth.W))),
    Vec(Seq.fill(hardwareConfig.XNORFanout){0.U(hardwareConfig.XNORBitWidth.W)})
  )

  val meanBuffer=Module(new MeanBuffer(
    hardwareConfig.opWidth,
    hardwareConfig.XNORFanout))

  //Mean

  val mean=Module(new DelayedOutput(hardwareConfig.opWidth, 1))
  mean.io.update:=io.meanUpdate
  mean.io.reset:=io.meanReset

  //Max
  val maxModule=Module(new MaxBuffer(
    hardwareConfig.opWidth,
    hardwareConfig.resultWidth,
    hardwareConfig.XNORFanout))
  maxModule.io.en:=io.maxEn
  maxModule.io.reset:=io.maxReset
  maxModule.io.offset:=io.maxOffset
  io.result:=maxModule.io.out

  val signs=for(i<-0 until hardwareConfig.XNORFanout) yield {
    //for each XNOR vector, go to accumulator, then go to a) BN buffer, and b) max
    val acc = Module(new Accumulator(
      hardwareConfig.opWidth,
      maxAccWidth))  //selection
    acc.io.in := BitSum(
      hardwareConfig.XNORBitWidth,
      log2Ceil(hardwareConfig.XNORBitWidth)+1) //result will never exceeds XNOR bitwidth
      .generate(xnor.io.out(i))
    acc.io.sel := io.accSel
    acc.io.reset := io.accReset
    acc.io.en:=io.accEn


    val mulAdd = Module(new MulAdd(
      hardwareConfig.bnParamWidth,
      hardwareConfig.opWidth))

    //choose to use a Sel or not
    if(bnCnt>=hardwareConfig.XNORFanout) {
      //no need to have Sel, direct map
      val offset = (bnCnt - i) * hardwareConfig.bnParamWidth * 2
      val bnPair = Mux(
        io.memSel,
        0.U((hardwareConfig.bnParamWidth*2).W),
        memOut(offset - 1, offset - hardwareConfig.bnParamWidth * 2)
      )
      mulAdd.io.a := bnPair(hardwareConfig.bnParamWidth * 2 - 1, hardwareConfig.bnParamWidth).asSInt()
      mulAdd.io.c := bnPair(hardwareConfig.bnParamWidth - 1, 0).asSInt()
    }
    else if(bnCnt<hardwareConfig.XNORFanout){
      //TODO
      //set up a register to save the result
      val bnBuffer = Reg(Bits((hardwareConfig.bnParamWidth*2).W))
      val offset = ((hardwareConfig.XNORFanout - i-1)%bnCnt+1) * hardwareConfig.bnParamWidth * 2
      bnBuffer:=Mux(
        io.memSel,
        0.U((hardwareConfig.bnParamWidth*2).W),
        mem.io.out(offset-1, offset-hardwareConfig.bnParamWidth*2)
      )(hardwareConfig.bnParamWidth*2*bnCnt-1, 0)
    }
    mulAdd.io.m:=mean.io.output
    mulAdd.io.b:=acc.io.out
    meanBuffer.io.in(i):=mulAdd.io.r
    maxModule.io.in(i):=mulAdd.io.r
    mulAdd.io.r(31)
  }


  inputWire:=Cat(signs)

  meanBuffer.io.reset:=io.meanUpdate
  meanBuffer.io.cntInverse65536:=io.featureNumInverse65536
  mean.io.input:=meanBuffer.io.out
}
