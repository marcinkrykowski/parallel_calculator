package rest

import akka.http.scaladsl.Http.ServerBinding
import java.text.DecimalFormat

import scala.concurrent._
import scala.io.StdIn
import scala.util.{Failure, Success}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream._
import domain.{Expression, Calculator}
import spray.json._

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val jsonInput: RootJsonFormat[JsonExpressionInput] = jsonFormat1(
    JsonExpressionInput
  )
  implicit object jsonOutput extends RootJsonFormat[JsonExpressionResult] {
    def write(res: JsonExpressionResult): JsObject =
      JsObject(
        "result" -> JsNumber(new DecimalFormat("#.###").format(res.result))
      )

    def read(value: JsValue): JsonExpressionResult =
      value.asJsObject.getFields("result") match {
        case Seq(JsNumber(res)) => JsonExpressionResult(res.toDouble)
        case _                  => deserializationError("Error while reading result")
      }
  }
}

final case class JsonExpressionInput(expression: String)
final case class JsonExpressionResult(result: Double)

trait RestService extends Directives with JsonSupport {
  implicit val system: ActorSystem
  implicit val materializer: Materializer
  val route: Route =
    path("evaluate") {
      post {
        entity(as[JsonExpressionInput]) { stringExpression =>
          Calculator.evaluate(stringExpression.expression) match {
            case Left(err) =>
              complete(StatusCodes.UnprocessableEntity -> err.reason)
            case Right(futureRes) =>
              onComplete(futureRes) {
                case Success(res) =>
                  if (res.isInfinite)
                    complete(
                      StatusCodes.UnprocessableEntity -> Expression
                        .DivisionByZeroError()
                        .reason
                    )
                  else
                    complete(JsonExpressionResult(res))
                case Failure(_) =>
                  complete(
                    StatusCodes.InternalServerError -> Expression
                      .StreamProcessingError()
                      .reason
                  )
              }
          }
        }
      }
    }
}

class RestServer(implicit
    val system: ActorSystem,
    val materializer: Materializer
) extends RestService {
  def startServer(addr: String, port: Int): Future[ServerBinding] = {
    Http().newServerAt(addr, port).bindFlow(route)
  }
}

object CalculatorServer extends Directives with JsonSupport {

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("test_app")
    implicit val materializer: Materializer =
      Materializer.createMaterializer(system)
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    val bindingFuture =
      new RestServer()(system, materializer).startServer("localhost", 5555)
    println(
      s"Server started at http://localhost:5555/\nPress RETURN to stop..."
    )
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}
