package org.constellation.playground

import cats.data.Kleisli
import cats.effect.IO
import higherkindness.droste.data.Fix
import higherkindness.droste.scheme
import org.constellation.playground.schema.Cell.{fiber, printAlgebra}
import org.constellation.playground.schema.{Bundle, Cell, Fiber, Lookup, Result}
import org.scalatest.{FreeSpec, Matchers}

class DummyTest extends FreeSpec with Matchers {
  "foo" in {

    val fixedCell: Fix[Cell] = Fix(Lookup(Fix(Result(fiber))))

    val print = scheme.cata(printAlgebra)

    // ---

    val res = print.apply(fixedCell)
    println(res)

    "a" shouldBe "a"
  }
}
