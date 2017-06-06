package xnornet4fpga

// See LICENSE.txt for license details.
import chisel3._
import chisel3.iotesters.PeekPokeTester

object Main {
  def main(args: Array[String]): Unit = {
    val generator=()=>new IglooScheduler(HardwareConfig.twoway, SimpleMLP(256, 10, 96))
    chisel3.Driver.execute(args, generator)
  }
}
