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
class XNORNetInference(hardwareConfig: HardwareConfig, mem:AggregateMem
                      ) extends Module {
  val maxAccWidth=hardwareConfig.maxFeatures/hardwareConfig.XNORFanout //max # rounds of computations
  val bnCnt=hardwareConfig.memLineWidth/(hardwareConfig.bnParamWidth*2) //# of bn param pairs loaded each clock
  val bnFanout=hardwareConfig.XNORFanout/bnCnt //TODO:now only support 1
  val aggregateMemWidth=hardwareConfig.memCnt*(hardwareConfig.XNORBitWidth*hardwareConfig.XNORFanout) //only these bits are used

  val io=IO(new Bundle{
    val input=Input(Bits(hardwareConfig.XNORFanout.W))
    val bnBufferSel=Input(Bits(log2Ceil(bnFanout).W))
    val inputSel=Input(Bool())
    val inputBufferPush=Input(Bool())
    val inputBufferPop=Input(Bool())
    val memSel=Input(Bool())
    val accEn=Input(Bool())
    val accSel=Input(UInt(log2Ceil(maxAccWidth).W))
    val accReset=Input(Bool())
    val maxClear=Input(Bool())
    val maxEn=Input(Bool())
    val maxOffset=Input(UInt(hardwareConfig.resultWidth.W))
    val meanEn=Input(Bool())
    val meanReset=Input(Bool())
    val featureNum=Input(UInt(8.W))
    val meanMux=Input(Bool())

    val result=Output(UInt(hardwareConfig.resultWidth.W))
  })

  val inputWire=Wire(Bits(hardwareConfig.XNORFanout.W)) //connected by the output signs

  val binaryBuffer=Module(new BinaryBuffer(
    Math.max(hardwareConfig.maxFeatures, hardwareConfig.maxInputWidth),  //storage size in bits
    hardwareConfig.XNORBitWidth,  //output width
    hardwareConfig.XNORFanout))   //input width
  binaryBuffer.io.in:=Mux(io.inputSel, io.input, inputWire)
  binaryBuffer.io.push:=io.inputBufferPush
  binaryBuffer.io.pop:=io.inputBufferPop

  val xnor=Module(new XNOR(
    hardwareConfig.XNORBitWidth, //input width
    hardwareConfig.XNORFanout))  //how many vectors are XNORed parallel
  xnor.io.in1:=binaryBuffer.io.out

  val memOut=mem.io.out(aggregateMemWidth-1, 0)

  xnor.io.in2:=Mux(
    io.memSel,
    memOut,
    0.U(hardwareConfig.memLineWidth.W)
  )(hardwareConfig.memLineWidth-1-hardwareConfig.spareBandwidth, 0) //remove unused bits

  val meanWire=Wire(Bits(hardwareConfig.opWidth.W)) //connected to mean buffer

  val meanBuffer=Module(new MeanBuffer(
    hardwareConfig.opWidth,
    hardwareConfig.XNORFanout))

  /*//setup the bn selectors
  if(bnCnt<hardwareConfig.XNORFanout) {
    for (i <- 0 until bnCnt) {
      if(bnFanout==2) {
        val offset = (bnCnt - i) * hardwareConfig.bnParamWidth * 2
        mem.io.out(offset - 1, offset - hardwareConfig.bnParamWidth * 2)
      }
    }
  }
*/

  //Max
  val maxModule=Module(new MaxBuffer(
    hardwareConfig.opWidth,
    hardwareConfig.resultWidth,
    hardwareConfig.XNORFanout))
  maxModule.io.en:=io.maxEn
  maxModule.io.reset:=io.maxClear
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
      mulAdd.io.a := bnPair(hardwareConfig.bnParamWidth * 2 - 1, hardwareConfig.bnParamWidth)
      mulAdd.io.c := bnPair(hardwareConfig.bnParamWidth - 1, 0)
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
    mulAdd.io.m:=meanWire
    mulAdd.io.b:=acc.io.out
    meanBuffer.io.in(i):=mulAdd.io.r
    maxModule.io.in(i):=mulAdd.io.r
    mulAdd.io.r(31)
  }


  inputWire:=Cat(signs)

  meanBuffer.io.en:=io.meanEn
  meanBuffer.io.reset:=io.meanReset
  meanBuffer.io.cnt:=io.featureNum
  meanWire:=meanBuffer.io.out
/*
  def generateBitmap(params:Iterator[Any], hwConfig:HardwareConfig, dest:String){
    val writer=(0 until hwConfig.memCnt) map {i=>new FileOutputStream(dest+i)}
    val bitPerMem=hwConfig.memLineWidth/8*8
    var currMem=0
    var memOffset=0

    def append(bit:Boolean): Unit = {
      writer(currMem).write(if (bit) '1' else '0')
      memOffset += 1
      if (memOffset == bitPerMem) {
        memOffset = 0
        currMem = (currMem + 1) % hwConfig.memCnt
      }
    }
    while(params.hasNext){
      val content=params.next()
      content match{
        case bit:Boolean=> {
          append(bit)
        }
        case numPair:(Int, Int)=>{
          val bitNum=hwConfig.bnParamWidth
          val num=((numPair._1&((1<<bitNum)-1))|((numPair._2&((1<<bitNum)-1))<<bitNum))
          for(x<-0 until bitNum*2){
            append((num&(1<<(bitNum*2-x-1)))>0) //first is MSB
          }
        }
      }
    }
  }

  def generateParamIterator(nNTopology: NNTopology): Unit ={
    (0 until nNTopology.getTotalLayers()) foldLeft(Iterator[Any]()){case (it, layer)=>{

    }}
  }
  */
}
