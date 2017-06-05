package xnornet4fpga
import chisel3._
import chisel3.iotesters.PeekPokeTester
import chisel3.util._

/**
  * Created by wanga on 2017/6/3.
  */
object TestAll extends App {

  def test[T <: PeekPokeTester[M], M <: Module](m: => M)(t: M => T): Unit = {
    assert(chisel3.iotesters.Driver(m _) { x => t(x) })
  }

  val hwConfig = HardwareConfig.default

  //test
  test(new Accumulator(32, 4))(new AccumulatorTest(_))
  test(new BinaryBuffer(256, 32, 128, 4))(new BinaryBufferTest(_, hwConfig))
  test(new BitSumModule())(new BitSumTest(_))
  test(new DelayedOutput(32, 1))(new DelayedOutputTest(_, 1))
  test(new MaxBuffer(32, 16, 4))(new MaxBufferTest(_, 4))
  test(new MeanBuffer(32, 4))(new MeanBufferTest(_, 4))
  test(new XNOR(32, 4))(new XNORTest(hwConfig, _))
  test(new MemTestModule(hwConfig, 16))(new MemTest(_))

  val topo=SimpleMLP(256,10,96)
  test(new IglooScheduler(hwConfig, topo))(new InferenceTest(_, topo, hwConfig))
}
