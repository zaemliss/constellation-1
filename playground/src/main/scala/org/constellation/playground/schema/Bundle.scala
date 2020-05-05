package org.constellation.playground.schema

class Bundle(fibers: Seq[Fiber]) extends Fiber

object Bundle {
  val example: Fiber = new Bundle(Seq(new Bundle(Seq.empty)))
}
