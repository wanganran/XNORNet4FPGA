package xnornet4fpga

import chisel3.iotesters.PeekPokeTester

/**
  * Created by wanga on 2017/5/31.
  */
class AccumulatorTest(acc:Accumulator) extends PeekPokeTester(acc) {
  //test reset
  poke(acc.io.en, true)
  poke(acc.io.reset, true)
  poke(acc.io.sel, 0)
  step(1)
  expect(acc.io.out, 0)

  //test simple acc
  poke(acc.io.reset, false)
  poke(acc.io.in, 10)
  step(1)
  expect(acc.io.out, 10)

  //test continuous acc
  poke(acc.io.in, 5)
  step(1)
  expect(acc.io.out, 15)
  step(1)
  expect(acc.io.out, 20)

  //test switch sel
  poke(acc.io.sel, 1)
  step(1)
  expect(acc.io.out, 5)
  poke(acc.io.sel, 0)
  poke(acc.io.in, 0)
  step(1)
  expect(acc.io.out, 20)

  //test reset
  poke(acc.io.reset, true)
  step(1)
  expect(acc.io.out, 0)
}
