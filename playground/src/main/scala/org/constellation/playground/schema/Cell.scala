package org.constellation.playground.schema

import cats.{FlatMap, Functor, Monad}
import cats.data.Kleisli
import cats.effect.{IO, Sync}
import cats.implicits._
import higherkindness.droste.data.Fix
import higherkindness.droste.{Algebra, AlgebraM, Coalgebra, scheme}
import org.constellation.playground.schema.Cell.K

sealed trait Cell[A] {}
case class Result[A](value: Fiber) extends Cell[A]
case class Lookup[A](value: A) extends Cell[A]
case class Upsert[A](value: A) extends Cell[A]
case class Compute[A](value: A) extends Cell[A]

object Cell {
  type K[F[_], A] = Kleisli[F, Cell[A], Fiber]

  implicit val cellFunctorImpl: Functor[Cell] = new Functor[Cell] {
    override def map[A, B](fa: Cell[A])(f: A => B): Cell[B] = fa match {
      case Result(a)  => Result(a)
      case Lookup(a)  => Lookup(f(a))
      case Upsert(a)  => Upsert(f(a))
      case Compute(a) => Compute(f(a))
    }
  }

  implicit val cellFlatMapImpl: FlatMap[Cell] = new FlatMap[Cell] {
    override def flatMap[A, B](fa: Cell[A])(f: A => Cell[B]): Cell[B] = fa match {
      case Result(a)  => Result(a)
      case Lookup(a)  => f(a)
      case Upsert(a)  => f(a)
      case Compute(a) => f(a)
    }

    override def tailRecM[A, B](a: A)(f: A => Cell[Either[A, B]]): Cell[B] = ???

    override def map[A, B](fa: Cell[A])(f: A => B): Cell[B] = cellFunctorImpl.map(fa)(f)
  }

  val resultCoalgebra: Coalgebra[Cell, Fiber] =
    Coalgebra[Cell, Fiber](fiber => Result(fiber))

  case class Context(database: String)

  def lookup[F[_]](s: String)(implicit F: Sync[F]): Kleisli[F, Context, String] = Kleisli.apply { ctx =>
    F.pure(s"{ s: ${s} | lookup for db: ${ctx.database} }")
  }
  def upsert[F[_]]: Kleisli[F, Context, Fiber] = ???
  def compute[F[_]]: Kleisli[F, Context, Fiber] = ???

  // TODO: use Fiber instead of String

  val executeAlgebra: Algebra[Cell, Kleisli[IO, Context, String]] =
    Algebra {
      case Result(a) => Kleisli.pure("result")
      case Lookup(a) =>
        a.flatMap { s =>
          lookup[IO](s)
        }
      case Upsert(_)  => ???
      case Compute(_) => ???
    }

  val execute = scheme.cata(executeAlgebra)

  val printAlgebra: Algebra[Cell, String] =
    Algebra[Cell, String] {
      case Result(a)  => s"result: ${a}"
      case Lookup(a)  => s"lookup: ${a}"
      case Upsert(a)  => s"upsert: ${a}"
      case Compute(a) => s"compute; ${a}"
    }

  val print = scheme.cata(printAlgebra)
}
