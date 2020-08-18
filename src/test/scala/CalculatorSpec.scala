import akka.http.scaladsl.model.{
  HttpEntity,
  HttpMethods,
  HttpRequest,
  MediaTypes,
  StatusCodes
}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.Sink
import akka.testkit.TestProbe
import akka.util.ByteString
import domain.{Expression, Add}
import org.scalatest.matchers.should.Matchers
import rest.{JsonExpressionResult, RestService}
import domain._
import org.scalatest.flatspec.AnyFlatSpec

import scala.concurrent.duration._

class CalculatorSpec
    extends AnyFlatSpec
    with Matchers
    with ScalatestRouteTest
    with RestService {

  def postRequest(expr: String): HttpRequest = {
    val jsonRequest = ByteString(s"""
                                    |{
                                    |    "expression": "$expr"
                                    |}
    """.stripMargin)

    HttpRequest(
      HttpMethods.POST,
      uri = "/evaluate",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest)
    )
  }

  "The http server" should "return correct result for data from task specification" in {
    postRequest("(1-1)*2+3*(1-3+4)+10/2") ~> route ~> check {
      status.isSuccess() shouldEqual true
      responseAs[JsonExpressionResult].result shouldEqual 11
    }
  }

  (it should "correctly handle expression with division by 0").in({
    postRequest("1/0") ~> route ~> check {
      status shouldEqual StatusCodes.UnprocessableEntity
      responseAs[String] shouldEqual Expression.DivisionByZeroError().reason
    }
  })

  "Correct expression" should "be evaluated on a single thread" in {
    Add(
      Number(4),
      Add(Multiply(Number(1), Number(3)), Multiply(Number(0), Number(19)))
    ).evaluate should be(7)
  }

  it should "parse expressions right" in {
    Calculator.stringToExpression("2+(1*5)") shouldEqual Right(
      Add(Number(2), Multiply(Number(1), Number(5)))
    )
    Calculator.stringToExpression(
      "(((5)))+(8*2/(7-4))"
    ) shouldEqual Right(
      Add(
        Number(5),
        Divide(Multiply(Number(8), Number(2)), Subtract(Number(7), Number(4)))
      )
    )
  }

  it should "report error on wrong expression" in {
    def exprError(s: String) =
      Calculator.stringToExpression(s) should matchPattern {
        case Left(Expression.ParsingError(_)) =>
      }
    exprError("*13")
    exprError("")
    exprError("1*13/")
    exprError("*((5)+()")
    exprError("((9+8)")
  }

  "StreamsGraph" should "evaluate given expression right" in {
    def streamTest(e: Expression, res: Double): Double = {
      val probe = TestProbe()
      CalculatorStreaming
        .exprToStream(e)
        .to(Sink.actorRef(probe.ref, "completed"))
        .run()
      probe.expectMsg(1.second, res)
    }
    streamTest(
      Add(Multiply(Number(5), Number(2)), Divide(Number(8), Number(4))),
      12
    )
    streamTest(
      Multiply(Multiply(Multiply(Number(1), Number(3)), Number(5)), Number(7)),
      105
    )
    streamTest(Divide(Number(1), Number(0)), Double.PositiveInfinity)
  }
}
