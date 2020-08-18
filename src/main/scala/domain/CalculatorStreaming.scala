package domain

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent._

object CalculatorStreaming {
  type ExprRes = Expression.Result
  type ExprSource = Source[ExprRes, NotUsed]
  type ExprOp = (ExprRes, ExprRes) => ExprRes

  def apply(expr: Expression): RunnableGraph[Future[Expression.Result]] =
    exprToStream(expr).toMat(Sink.head[Expression.Result])(Keep.right)

  def exprToStream(expr: Expression): ExprSource = {
    expr match {
      case Multiply(l, r) => exprToGraph(l, r, _ * _)
      case Divide(l, r)   => exprToGraph(l, r, _ / _)
      case Add(l, r)      => exprToGraph(l, r, _ + _)
      case Subtract(l, r) => exprToGraph(l, r, _ - _)
      case Number(x)      => Source.single(x)
    }
  }

  def makeGraph(s0: ExprSource, s1: ExprSource, op: ExprOp): ExprSource = {
    Source.fromGraph(
      GraphDSL
        .create() { implicit b =>
          import GraphDSL.Implicits._
          val zipOp = b.add(ZipWith[ExprRes, ExprRes, ExprRes](op))
          s0 ~> zipOp.in0
          s1 ~> zipOp.in1
          SourceShape(zipOp.out)
        }
        .async
    )
  }

  def exprToGraph(l: Expression, r: Expression, op: ExprOp): ExprSource = {
    (l, r) match {
      case (Number(x), Number(y)) =>
        makeGraph(Source.single(x), Source.single(y), op)
      case (xExpr, Number(y)) =>
        makeGraph(exprToStream(xExpr), Source.single(y), op)
      case (Number(x), yExpr) =>
        makeGraph(Source.single(x), exprToStream(yExpr), op)
      case (xExpr, yExpr) =>
        makeGraph(exprToStream(xExpr), exprToStream(yExpr), op)
    }
  }
}
