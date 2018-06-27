package org.constellation.primitives


import java.net.InetSocketAddress
import java.security.PublicKey

import constellation.pubKeyToAddress
import org.constellation.crypto.Base58
import org.constellation.util.{EncodedPublicKey, HashSignature, ProductHash, Signed}

import scala.collection.concurrent.TrieMap
import scala.util.Random

// This can't be a trait due to serialization issues
object Schema {

  case class TreeVisual(
                       name: String,
                       parent: String,
                       children: Seq[TreeVisual]
                       )

  case class TransactionQueryResponse(
                                       hash: String,
                                       tx: Option[TX],
                                       observed: Boolean,
                                       inMemPool: Boolean,
                                       confirmed: Boolean,
                                       numGossipChains: Int,
                                       gossipStackDepths: Seq[Int],
                                       gossip: Seq[Gossip[ProductHash]]
                                     )

  sealed trait NodeState
  final case object PendingDownload extends NodeState
  final case object Ready extends NodeState

  sealed trait ValidationStatus

  final case object Valid extends ValidationStatus
  final case object MempoolValid extends ValidationStatus
  final case object Unknown extends ValidationStatus
  final case object DoubleSpend extends ValidationStatus


  sealed trait ConfigUpdate

  final case class ReputationUpdates(updates: Seq[UpdateReputation]) extends ConfigUpdate

  case class UpdateReputation(id: Id, secretReputation: Option[Double], publicReputation: Option[Double])

  // I.e. equivalent to number of sat per btc
  val NormalizationFactor: Long = 1e8.toLong

  case class SendToAddress(
                            address: AddressMetaData,
                            amount: Long,
                            account: Option[PublicKey] = None,
                            normalized: Boolean = true,
                            oneTimeUse: Boolean = false,
                            useNodeKey: Boolean = true,
                            doGossip: Boolean = true
                          ) {
    def amountActual: Long = if (normalized) amount * NormalizationFactor else amount
  }

  // TODO: We also need a hash pointer to represent the post-tx counter party signing data, add later
  // TX should still be accepted even if metadata is incorrect, it just serves to help validation rounds.
  case class AddressMetaData(
                      address: String,
                      balance: Long = 0L,
                      lastValidTransactionHash: Option[String] = None,
                      txHashPool: Seq[String] = Seq(),
                      txHashOverflowPointer: Option[String] = None,
                      oneTimeUse: Boolean = false,
                      depth: Int = 0
                    ) extends ProductHash {
    def normalizedBalance: Long = balance / NormalizationFactor
  }

  sealed trait Fiber

  case class TXData(
                     src: String,
                     dst: String,
                     amount: Long,
                     time: Long = System.currentTimeMillis()
                   ) extends ProductHash {
    def inverseAmount: Long = -1*amount
    def normalizedAmount: Long = amount / NormalizationFactor
  }

  case class TX(
                 hashSignature: HashSignature
               ) extends ProductHash with Fiber {
  }

  sealed trait GossipMessage

  final case class ConflictDetectedData(detectedOn: TX, conflicts: Seq[TX]) extends ProductHash

  final case class ConflictDetected(conflict: Signed[ConflictDetectedData]) extends ProductHash with GossipMessage

  final case class VoteData(accept: Seq[TX], reject: Seq[TX]) extends ProductHash {
    // used to determine what voting round we are talking about
    def voteRoundHash: String = {
      accept.++(reject).sortBy(t => t.hashCode()).map(f => f.hash).mkString("-")
    }
  }

  final case class VoteCandidate(tx: TX, gossip: Seq[Gossip[ProductHash]])

  final case class VoteDataSimpler(accept: Seq[VoteCandidate], reject: Seq[VoteCandidate]) extends ProductHash

  final case class Vote(vote: Signed[VoteData]) extends ProductHash with Fiber

  // Participants are notarized via signatures.
  final case class BundleBlock(
                                parentHash: String,
                                height: Long,
                                txHash: Seq[String]
                              ) extends ProductHash with Fiber

  final case class BundleHash(hash: String) extends Fiber
  final case class TransactionHash(hash: String) extends Fiber
  final case class ParentBundleHash(hash: String) extends Fiber

  // TODO: Make another bundle data with additional metadata for depth etc.
  final case class BundleData(bundles: Seq[Fiber]) extends ProductHash

  case class RequestBundleData(hash: String) extends GossipMessage

  case class UnknownParentHashSyncInfo(
                                      firstRequestTime: Long,
                                      lastRequestTime: Long,
                                      numRequests: Int
                                      )

  case class BundleMetaData(
                             height: Int,
                             numTX: Int,
                             numID: Int,
                             score: Double,
                             totalScore: Double,
                             parentHash: String,
                             rxTime: Long
                           )

  final case class PeerSyncHeartbeat(
                               bundle: Option[Bundle],
                  //             memPool: Set[TX] = Set(),
                               validBundleHashes: Seq[String]
                             ) extends GossipMessage

  final case class Bundle(
                           bundleData: Signed[BundleData]
                         ) extends ProductHash with Fiber with GossipMessage {

    val bundleNumber: Long = Random.nextLong()

    def extractTreeVisual: TreeVisual = {
      val parentHash = extractParentBundleHash.hash.slice(0, 5)
      def process(s: Signed[BundleData], parent: String): Seq[TreeVisual] = {
        val bd = s.data.bundles
        val depths = bd.map {
          case tx: TX =>
            TreeVisual(s"TX: ${tx.short} amount: ${tx.tx.data.normalizedAmount}", parent, Seq())
          case b2: Bundle =>
            val name = s"Bundle: ${b2.short} numTX: ${b2.extractTX.size}"
            val children = process(b2.bundleData, name)
            TreeVisual(name, parent, children)
          case _ => Seq()
        }
        depths
      }.asInstanceOf[Seq[TreeVisual]]
      val parentName = s"Bundle: $short numTX: ${extractTX.size} node: ${bundleData.id.short} parent: $parentHash"
      val children = process(bundleData, parentName)
      TreeVisual(parentName, "null", children)
    }

    def extractParentBundleHash: ParentBundleHash = {
      def process(s: Signed[BundleData]): ParentBundleHash = {
        val bd = s.data.bundles
        val depths = bd.collectFirst{
          case b2: Bundle =>
            process(b2.bundleData)
          case bh: ParentBundleHash => bh
        }
        depths.get
      }
      process(bundleData)
    }

    def extractBundleHashId: (BundleHash, Id) = {
      def process(s: Signed[BundleData]): (BundleHash, Id) = {
        val bd = s.data.bundles
        val depths = bd.collectFirst{
          case b2: Bundle =>
            process(b2.bundleData)
          case bh: BundleHash => bh -> s.id
        }.get
        depths
      }
      process(bundleData)
    }

    def extractSubBundlesMinSize(minSize: Int = 2) = {
      extractSubBundles.filter{_.maxStackDepth >= minSize}
    }

    def extractSubBundles: Set[Bundle] = {
      def process(s: Signed[BundleData]): Set[Bundle] = {
        val bd = s.data.bundles
        val depths = bd.map {
          case b2: Bundle =>
            Set(b2) ++ process(b2.bundleData)
          case _ => Set[Bundle]()
        }
        depths.reduce(_ ++ _)
      }
      process(bundleData)
    }

    def extractTX: Set[TX] = {
      def process(s: Signed[BundleData]): Set[TX] = {
        val bd = s.data.bundles
        val depths = bd.map {
          case b2: Bundle =>
            process(b2.bundleData)
          case tx: TX => Set(tx)
          case _ => Set[TX]()
        }
        if (depths.nonEmpty) {
          depths.reduce(_ ++ _)
        } else {
          Set()
        }
      }
      process(bundleData)
    }

    def extractIds: Set[Id] = {
      def process(s: Signed[BundleData]): Set[Id] = {
        val bd = s.data.bundles
        val depths = bd.map {
          case b2: Bundle =>
            b2.bundleData.publicKeys.map{Id}.toSet ++ process(b2.bundleData)
          case _ => Set[Id]()
        }
        depths.reduce(_ ++ _) ++ s.publicKeys.map{Id}.toSet
      }
      process(bundleData)
    }

    def maxStackDepth: Int = {
      def process(s: Signed[BundleData]): Int = {
        val bd = s.data.bundles
        val depths = bd.map {
          case b2: Bundle =>
            process(b2.bundleData) + 1
          case _ => 0
        }
        depths.max
      }
      process(bundleData) + 1
    }

    def totalNumEvents: Int = {
      def process(s: Signed[BundleData]): Int = {
        val bd = s.data.bundles
        val depths = bd.map {
          case b2: Bundle =>
            process(b2.bundleData) + 1
          case _ => 1
        }
        depths.sum
      }
      process(bundleData) + 1
    }

    def roundHash: String = {
      bundleNumber.toString
    }

  }

  final case class Gossip[T <: ProductHash](event: Signed[T]) extends ProductHash
    with Fiber
    with GossipMessage {
    def iter: Seq[Signed[_ >: T <: ProductHash]] = {
      def process[Q <: ProductHash](s: Signed[Q]): Seq[Signed[ProductHash]] = {
        s.data match {
          case Gossip(g) =>
            val res = process(g)
            res :+ g
          case _ => Seq()
        }
      }
      process(event) :+ event
    }
    def stackDepth: Int = {
      def process[Q <: ProductHash](s: Signed[Q]): Int = {
        s.data match {
          case Gossip(g) =>
            process(g) + 1
          case _ => 0
        }
      }
      process(event) + 1
    }

  }

  // TODO: Move other messages here.
  sealed trait InternalCommand

  final case object GetPeersID extends InternalCommand
  final case object GetPeersData extends InternalCommand
  final case object GetUTXO extends InternalCommand
  final case object GetValidTX extends InternalCommand
  final case object GetMemPoolUTXO extends InternalCommand
  final case object ToggleHeartbeat extends InternalCommand
  final case object InternalHeartbeat extends InternalCommand
  final case object InternalBundleHeartbeat extends InternalCommand

  final case class ValidateTransaction(tx: TX) extends InternalCommand

  sealed trait DownloadMessage

  case class DownloadRequest() extends DownloadMessage
  case class DownloadResponse(
                               genesisBundle: Bundle,
                               validBundles: Seq[Bundle],
                               ledger: Map[String, Long],
                               lastCheckpointBundle: Option[Bundle]
                             ) extends DownloadMessage

  final case class SyncData(validTX: Set[TX], memPoolTX: Set[TX]) extends GossipMessage

  case class MissingTXProof(tx: TX, gossip: Seq[Gossip[ProductHash]]) extends GossipMessage

  final case class RequestTXProof(txHash: String) extends GossipMessage

  case class Metrics(metrics: Map[String, String])

  final case class AddPeerFromLocal(address: InetSocketAddress) extends InternalCommand

  case class Peers(peers: Seq[InetSocketAddress])

  case class Id(id: PublicKey) {
    def short: String = id.toString.slice(15, 20)
    def medium: String = id.toString.slice(15, 25).replaceAll(":", "")
    def address: AddressMetaData = pubKeyToAddress(id)
    def b58 = Base58.encode(id.getEncoded)
  }

  case class GetId()

  case class GetBalance(account: PublicKey)

  case class HandShake(
                        originPeer: Signed[Peer],
                        requestExternalAddressCheck: Boolean = false
                        //           peers: Seq[Signed[Peer]],
                        //          destination: Option[InetSocketAddress] = None
                      ) extends ProductHash

  // These exist because type erasure messes up pattern matching on Signed[T] such that
  // you need a wrapper case class like this
  case class HandShakeMessage(handShake: Signed[HandShake])
  case class HandShakeResponseMessage(handShakeResponse: Signed[HandShakeResponse])

  case class HandShakeResponse(
                                //                   original: Signed[HandShake],
                                response: HandShake,
                                detectedRemote: InetSocketAddress
                              ) extends ProductHash

  case class Peer(
                   id: Id,
                   externalAddress: InetSocketAddress,
                   remotes: Set[InetSocketAddress] = Set(),
                   apiAddress: InetSocketAddress = null
                 ) extends ProductHash





  // Experimental

  case class CounterPartyTXRequest(
                                    dst: AddressMetaData,
                                    counterParty: AddressMetaData,
                                    counterPartyAccount: Option[EncodedPublicKey]
                                  ) extends ProductHash


}