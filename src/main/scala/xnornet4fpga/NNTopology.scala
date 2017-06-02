package xnornet4fpga

/**
  * Created by wanga on 2017/6/1.
  */
trait NNTopology {
  def getTotalLayers(): Int
  def apply(layer: Int):(Int, Int)
  def getParameterSizeType(layer: Int): Array[(Int, Class[_])]
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
  override def getParameterSizeType(layer: Int) = {
    if (layer == 0) Array((inputSize, classOf[Boolean]))
    else if(getTotalLayers()==1)
      Array((inputSize * outputSize, classOf[Boolean]),
        (outputSize, classOf[(Int, Int)]))
    else if (layer != hiddenLayer.length + 1)
      Array(
        ((if (layer == 1) inputSize else hiddenLayer(layer - 2)) * hiddenLayer(layer - 1), classOf[Boolean]),
        (hiddenLayer(layer - 1), classOf[(Int, Int)]))
    else if (layer == hiddenLayer.length + 1)
      Array((hiddenLayer.last * outputSize, classOf[Boolean]),
      (outputSize, classOf[(Int, Int)]))
    else throw new Exception("layer out of bound")
  }

  override def getTotalLayers() = hiddenLayer.length+1
}
