package xnornet4fpga

import chisel3.iotesters.PeekPokeTester

import scala.util.Random

/**
  * Created by wanga on 2017/6/3.
  */
class DelayedOutputTest(d:DelayedOutput, default:Int) extends PeekPokeTester(d) {
  poke(d.io.reset, true)
  poke(d.io.update, false)
  step(1)
  expect(d.io.output, default)

  poke(d.io.reset, false)
  poke(d.io.update, false)
  poke(d.io.input, Random.nextInt(10000))
  step(1)
  expect(d.io.output, default)

  poke(d.io.update, true)
  poke(d.io.input, 234)
  step(1)

  expect(d.io.output, 234)
}
