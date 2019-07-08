package org.constellation.storage.transactions

import cats.effect.{Concurrent, LiftIO, Sync}
import cats.implicits._
import org.constellation.DAO
import org.constellation.primitives.Schema.Id
import org.constellation.primitives.TransactionCacheData
import org.constellation.storage.TransactionService

import scala.util.Random

class TransactionGossiping[F[_]: Concurrent](transactionService: TransactionService[F], fanout: Int, dao: DAO) {

  def selectPeers(tx: TransactionCacheData)(implicit random: Random): F[Set[Id]] =
    for {
      peers <- getDiffPeers(tx)
      randomPeers <- Sync[F].delay(random.shuffle(peers))
    } yield randomPeers.take(fanout)

  private def getDiffPeers(tx: TransactionCacheData): F[Set[Id]] =
    for {
      all <- LiftIO[F].liftIO(dao.peerInfo)
      used <- getUsedPeers(tx)
    } yield all.keySet.diff(used)

  private def getUsedPeers(tx: TransactionCacheData): F[Set[Id]] =
    tx.path.pure[F]

  def observe(tx: TransactionCacheData): F[TransactionCacheData] =
    for {
      isKnown <- transactionService.contains(tx.transaction.hash)
      updated <- if (isKnown) updateTxPath(tx) else setTxWithPath(tx)
    } yield updated

  private def setTxWithPath(tx: TransactionCacheData): F[TransactionCacheData] =
    transactionService.put(tx.copy(path = tx.path + dao.id), TransactionStatus.Unknown)

  private def updateTxPath(tx: TransactionCacheData): F[TransactionCacheData] =
    transactionService.update(tx.transaction.hash, t => t.copy(path = t.path ++ tx.path)) *>
      transactionService.lookup(tx.transaction.hash).map(_.get) // unsafe get but checked before if it exists

}
