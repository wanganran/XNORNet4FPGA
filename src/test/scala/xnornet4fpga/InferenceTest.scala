package xnornet4fpga

import chisel3.iotesters.PeekPokeTester

import scala.util.Random

/**
  * Created by wanga on 2017/6/4.
  */
class InferenceTest(scheduler: IglooScheduler, topo:NNTopology, hwConfig:HardwareConfig) extends PeekPokeTester(scheduler) {
  //first, generate data
  def randomInput()=Array.fill(topo(0)._1){Random.nextInt(2)}
  def inputMemory(in:Seq[Int])=Array(
    in.slice(0, 128).foldLeft(BigInt(0)){(a,b)=>a*2+b},
      in.slice(128, 256).foldLeft(BigInt(0)){(a,b)=>a*2+b}
  )
  def randomWeight() = Array(
    Array.fill(topo(0)._2) {
      Array.fill(topo(0)._1) {
        Random.nextInt(2)
      }
    },
    Array.fill(topo(0)._2) {
      Array(
        Random.nextInt(60000) - 30000,
        Random.nextInt(60000) - 30000)
    },
    Array.fill(topo(1)._2) {
      Array.fill(topo(1)._1) {
        Random.nextInt(2)
      }
    },
    Array.fill(topo(1)._2) {
      Array(
        Random.nextInt(60000) - 30000,
        Random.nextInt(60000) - 30000
      )
    })

  def calculate(in:Seq[Int], w:Seq[Array[Array[Int]]])={
    def xnor(a:Seq[Int], b:Array[Array[Int]])={
      def xnor(a:Seq[Int], b:Seq[Int])=a.zip(b).map { case (i,j)=>1-(i^j)}.sum
      for(i<-0 until b.length)yield{
        xnor(a,b(i))
      }
    }
    def bn(a:Seq[Int], b:Array[Array[Int]], s:Int=1)={
      val t=(a zip b) map {
        case (i, Array(j,k))=>i*j*s+k
      }
      (t map {x=>if(x>=0) 1 else 0}, (t.map(Math.abs).sum)/t.length, t.zipWithIndex.maxBy(_._1)._2)
    }

    val r1=bn(xnor(in, w(0)), w(1))
    val r2=bn(xnor(r1._1, w(2)), w(3), r1._2)
    r2._3
  }

  val weight=randomWeight()
  val input=randomInput()
  val gt=calculate(input, weight)

  //start testing

  val mems=topo.generateMemory(hwConfig, weight)
  print(mems.length)
  assert(mems.length==topo(0)._1*topo(0)._2/128+topo(0)._2/4+topo(1)._1*12/128+12/4)
  poke(scheduler.io.en, false)
  for(i<-0 until mems.length) {
    poke(scheduler.io.memWAddr, i)
    poke(scheduler.io.memWen, true)
    poke(scheduler.io.memIn, mems(i))

    step(1)
  }

  val inputMem=inputMemory(input)
  for(i<-0 until inputMem.length) {
    poke(scheduler.io.memWAddr, mems.length+i)
    poke(scheduler.io.memIn, inputMem(i))
    step(1)
  }

  //wait for writing
  step(3)
  poke(scheduler.io.memWen, false)


  poke(scheduler.io.en, true)
  poke(scheduler.io.memOffset, 0)
  poke(scheduler.io.inputOffset, mems.length)

  step(1)

  while(peek(scheduler.io.finished)!=1) {
    println(peek(scheduler.io.state).toString)
    step(1)
  }
  println(peek(scheduler.io.state).toString)
  expect(scheduler.io.result, gt)
}
