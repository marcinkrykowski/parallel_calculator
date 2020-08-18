package domain

import akka.stream._

import scala.concurrent._
import scala.util.parsing.combinator._

case class Multiply(left: Expression, right: Expression) extends Expression
case class Divide(left: Expression, right: Expression) extends Expression
case class Add(left: Expression, right: Expression) extends Expression
case class Subtract(left: Expression, right: Expression) extends Expression
case class Number(number: Expression.Result) extends Expression

object Calculator {
  def evaluate(s: String)(implicit
      mat: Materializer
  ): Either[Expression.Error, Future[Expression.Result]] =
    stringToExpression(s).map(CalculatorStreaming(_).run())

  def stringToExpression(s: String): Either[Expression.Error, Expression] = {
    val parsers = new ExpressionParsers()
    parsers.parseExpression(s) match {
      case parsers.Success(res, _)   => Right(res)
      case parsers.NoSuccess(err, _) => Left(Expression.ParsingError(err))
    }
  }

}

class ExpressionParsers extends JavaTokenParsers {
  def factorParser: Parser[Expression] = "(" ~> exprParser <~ ")" | numberParser
  def numberParser: Parser[Number] =
    floatingPointNumber ^^ { n => Number(n.toDouble) }

  def exprParser: Parser[Expression] =
    termParser ~! rep("+" ~! termParser | "-" ~! termParser) ^^ {
      case op ~ list =>
        list.foldLeft(op) {
          case (x, "+" ~ y) => Add(x, y)
          case (x, "-" ~ y) => Subtract(x, y)
        }
    }
  def termParser: Parser[Expression] =
    factorParser ~! rep("*" ~! factorParser | "/" ~! factorParser) ^^ {
      case op ~ list =>
        list.foldLeft(op) {
          case (x, "/" ~ y) => Divide(x, y)
          case (x, "*" ~ y) => Multiply(x, y)
        }
    }
  def parseExpression(expression: String): ParseResult[Expression] =
    parseAll(exprParser, expression)
}

sealed trait Expression {
  def evaluate: Expression.Result =
    this match {
      case Multiply(l, r) => l.evaluate * r.evaluate
      case Divide(l, r)   => l.evaluate / r.evaluate
      case Add(l, r)      => l.evaluate + r.evaluate
      case Subtract(l, r) => l.evaluate - r.evaluate
      case Number(n)      => n
    }
}

object Expression {
  type Result = Double
  type Level = Int
  sealed trait Error { def reason: String }
  case class StreamProcessingError(
      reason: String = "Stream processing error"
  ) extends Error
  case class DivisionByZeroError(
      reason: String = "Division by 0 is forbidden"
  ) extends Error
  case class ParsingError(reason: String) extends Error
}
