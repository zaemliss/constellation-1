package org.constellation

import java.lang
import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object ConfigUtil {

  val config: Config = ConfigFactory.load().resolve()
  val constellation = config.getConfig("constellation")

  val snapshotSizeDiskLimit: lang.Long =
    Try(config.getBytes("constellation.snapshot-size-disk-limit")).getOrElse(java.lang.Long.valueOf(1000000))

  val snapshotClosestFractionSize: Int = Try(config.getInt("constellation.snapshot-closest-fraction-size"))
    .getOrElse(12)

  val maxNestedCBresolution: Int = Try(config.getInt("constellation.max-nested-cb-resolution"))
    .getOrElse(100)

  def getOrElse(path: String, default: Int): Int =
    Try(config.getInt(path)).getOrElse(default)

  def getOrElse(path: String, default: Boolean): Boolean =
    Try(config.getBoolean(path)).getOrElse(default)

  def get(path: String): Try[String] = Try(config.getString(path))

  def getDurationFromConfig(path: String): FiniteDuration =
    FiniteDuration(config.getDuration(path, TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)

  def getDurationFromConfig(path: String, default: FiniteDuration, ownConfig: Config = config): FiniteDuration =
    Try(FiniteDuration(ownConfig.getDuration(path, TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS))
      .getOrElse(default)
}
