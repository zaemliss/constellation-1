package org.constellation.playground.schema

import cats.{FlatMap, Functor, Monad}
import cats.data.Kleisli
import cats.effect.IO
import cats.implicits._
import higherkindness.droste.data.Fix
import higherkindness.droste.{Algebra, AlgebraM, Coalgebra, scheme}

sealed trait Cell[A] {}
case class Result[A](value: Fiber) extends Cell[A] {
  def op[F[_]](): Kleisli[F, Cell[A], Fiber] = ???
}
case class Lookup[A](value: A) extends Cell[A] {
  def op[F[_]](): Kleisli[F, Cell[A], Fiber] = ???
}
case class Upsert[A](value: A) extends Cell[A] {
  def op[F[_]](): Kleisli[F, Cell[A], Fiber] = ???
}
case class Compute[A](value: A) extends Cell[A] {
  def op[F[_]](): Kleisli[F, Cell[A], Fiber] = ???
}

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

  val fiber: Fiber = new Bundle(Seq(new Fiber()))
  val cell: Cell[Fiber] = Lookup[Fiber](fiber)

  val resultCoalgebra: Coalgebra[Cell, Fiber] =
    Coalgebra[Cell, Fiber](fiber => Result(fiber))

//  val executeAlgebra: Algebra[Cell, Kleisli[IO, Cell, Fiber]] =
//    Algebra {
//      case Result(a)      => ???
//      case l @ Lookup(_)  => l.op[IO]()
//      case u @ Upsert(_)  => u.op[IO]()
//      case c @ Compute(_) => c.op[IO]()
//    }

//  val execute: Fix[Cell] => Kleisli[IO, Cell, Fiber] = scheme.cata(executeAlgebra)

  val printAlgebra: Algebra[Cell, String] =
    Algebra[Cell, String] {
      case Result(a)  => s"result: ${a}"
      case Lookup(a)  => s"lookup: ${a}"
      case Upsert(a)  => s"upsert: ${a}"
      case Compute(a) => s"compute; ${a}"
    }
}
