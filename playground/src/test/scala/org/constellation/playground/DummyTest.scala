package org.constellation.playground

import cats.data.Kleisli
import cats.effect.IO
import higherkindness.droste.data.Fix
import higherkindness.droste.scheme
import org.constellation.playground.schema.Cell.{Context, print, printAlgebra, execute => exe}
import org.constellation.playground.schema.{Bundle, Cell, Fiber, Lookup, Result}
import org.scalatest.{FreeSpec, Matchers}

class DummyTest extends FreeSpec with Matchers {
  "foo" in {

    val fiber = new Bundle(Seq.empty)
    val fixedCell: Fix[Cell] = Fix(Lookup(Fix(Lookup(Fix(Result(fiber))))))

    // ---

    val ctx: Context = Context("localdb")
    val res = exe.apply(fixedCell).run(ctx).unsafeRunSync()
    println(res)

    "a" shouldBe "a"
  }
}
