package io.github.tassiLuca.boundaries

import scala.util.boundary
import scala.util.boundary.{Label, break}

object result:

  sealed trait Result[+T]
  case class Ok[+T](t: T) extends Result[T]
  case class Error(e: String) extends Result[Nothing]

  inline def apply[T](inline body: Label[Error] ?=> T): Result[T] =
    boundary(Ok(body))

  extension [T](r: Result[T])
    inline def ?(using Label[Error]): T = r match
      case Ok(t) => t
      case e @ Error(_) => break(e)
