package xnornet4fpga

/**
  * Created by wanga on 2017/6/1.
  */
trait NNTopology {
  def getTotalLayers(): Int
  def apply(layer: Int):(Int, Int)
  def generateMemory(hwConfig:HardwareConfig, data:Seq[Array[_]]):Seq[BigInt]
}
case class SimpleMLP(
                     inputSize:Int,
                     outputSize:Int,
                     hiddenLayer:Int*) extends NNTopology {
  override def apply(layer:Int): (Int, Int) = {
    if(getTotalLayers()==1)(inputSize, outputSize)
    else if(layer==0)(inputSize, hiddenLayer(0))
    else if(layer<getTotalLayers()-1)(hiddenLayer(layer-1), hiddenLayer(layer))
    else if(layer==getTotalLayers()-1)(hiddenLayer(layer-1), outputSize)
    else throw new Exception("layer out of bound)")
  }
  override def getTotalLayers() = hiddenLayer.length+1

  override def generateMemory(hwConfig:HardwareConfig, data:Seq[Array[_]]): Seq[BigInt] = {
    //first check length
    assert(data.length==getTotalLayers()*2)
    def shape(arr:Array[_]):Seq[Int]=arr(0) match {
      case a:Array[_]=> arr.length +: shape(a)
      case _ => Seq(arr.length)
    }
    def same(a:Seq[Int], b:(Int, Int))=
      a.length==2 && a(0)==b._2 && a(1)==b._1
    for(i<-0 until getTotalLayers()){
      assert(same(shape(data(2*i)),this(i)))
      assert(shape(data(2*i+1))==Seq(this(i)._2, 2))
    }

    //then generate
    Range(0, getTotalLayers()).map{i=>
      val s=shape(data(2*i))
      println(s)
      val seq1=data(2*i) match {
        case arr: Array[Array[Int]] =>
          for (t1 <- 0 until s(1) by hwConfig.XNORBitWidth;
               t2 <- 0 until s(0) by hwConfig.XNORFanout) yield {
            var r = BigInt(0)
            for (i <- 0 until hwConfig.XNORFanout)
              for (j <- 0 until hwConfig.XNORBitWidth)
                r = r * 2 + (
                  if (t1 + j < s(1) && t2 + i < s(0) && arr(t2 + i)(t1 + j) > 0)
                    1
                  else 0
                  )
            r
          }
      }
      val s2=shape(data(2*i+1))
      println(s2)
      val seq2=data(2*i+1) match {
        case arr:Array[Array[Int]] =>
          for(t1<-0 until s2(0) by hwConfig.XNORFanout) yield {
            var r = BigInt(0)
            for (i <- 0 until hwConfig.XNORFanout)
              r = (r << 32) + (if (t1 + i < arr.length)
                ((BigInt(arr(t1 + i)(0)) & ((1 << 16) - 1)) << 16)
                  + (BigInt(arr(t1 + i)(1)) & ((1 << 16) - 1))
              else 0)
            r
          }
      }
      println(seq1.length+", "+seq2.length)
      seq1++seq2
    }.reduce(_++_)
  }
}
