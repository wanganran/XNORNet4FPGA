// See LICENSE.txt for license details.
import chisel3._
import chisel3.util._
import chisel3.iotesters.{Driver, PeekPokeTester}
import firrtl_interpreter.InterpretiveTester

import scala.io.Source

class Hello extends Module {
  val io = IO(new Bundle {
    val in=Input(UInt(16.W))
    val out = Output(UInt(8.W))
  })
  val padding=0.U(7.W)
  val r=RegInit(UInt(8.W),0.U)
  val x=RegInit(UInt(5.W),0.U)

  for(i<-0 until 16){
    when(x === i.U){
      r:=r+io.in(i)
      x:=x+1.U
    }
  }

  //val x=Cat(padding, io.in(0))+Cat(padding, io.in(1))+Cat(padding, io.in(2))+Cat(padding, io.in(3))+
  //Cat(padding, io.in(4))+Cat(padding, io.in(5))+Cat(padding, io.in(6))+Cat(padding, io.in(7))
  io.out := r
}

class HelloTests(c: Hello) extends PeekPokeTester(c) {
  poke(c.io.in, 255)
  step(1)
  print(peek(c.io.out))
  step(1)
  print(peek(c.io.out))
  step(1)
  print(peek(c.io.out))
  step(1)
  print(peek(c.io.out))
}

object Hello {
  def main(args: Array[String]): Unit = {
    chisel3.Driver.execute(args, ()=>new Hello())
    //new HelloTests(Source.fromFile("Hello.fir").mkString)
    if (!Driver(() => new Hello())(c => new HelloTests(c))) System.exit(1)
  }
}
