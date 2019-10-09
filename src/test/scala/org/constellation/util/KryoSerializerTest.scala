package org.constellation.util

import com.twitter.chill.KryoCustom
import com.twitter.chill.ProductEntity
import org.constellation.consensus.{RandomData, Snapshot, SnapshotInfo}
import org.constellation.primitives.Schema.CheckpointCache
import org.constellation.serializer.KryoSerializer
import org.scalatest.{FlatSpec, Matchers}

class KryoSerializerTest extends FlatSpec with Matchers {

  "KryoSerializer" should "serialize snapshot 2" in {

    val k = new KryoCustom()

    val bytes = k.write(SnapshotInfo(Snapshot("lastHash", Seq.empty)))
    println(bytes.length)

    val p = k.read(bytes, SnapshotInfo.getClass).asInstanceOf[SnapshotInfo]
    println(p.snapshot.lastSnapshot)

  }
  "KryoSerializer" should "serialize snapshot" in {

    val cb1 = RandomData.randomBlock(RandomData.startingTips)
    val cb2 = RandomData.randomBlock(RandomData.startingTips)
    val cbs = Seq(CheckpointCache(Some(cb1), 0, None), CheckpointCache(Some(cb2), 0, None))

    val snapshot = Snapshot("lastSnapHash", cbs.flatMap(_.checkpointBlock.map(_.baseHash)))
    val info: SnapshotInfo = SnapshotInfo(snapshot, snapshotCache = cbs)

    val bytes = KryoSerializer.serializeAnyRef(info)
    val bytesHacked = KryoSerializer.serializeAnyRefHacked(info)

    println(bytes.length)
    println(bytesHacked.length)

    val snapInfo = KryoSerializer.deserializeCast[SnapshotInfo](bytes)
    val snapInfoHacked = KryoSerializer.deserializeCastHacked[SnapshotInfo](bytesHacked)

    snapInfo shouldBe info
//    snapInfoHacked shouldBe info

  }
  /*
    "KryoSerializer" should "round trip serialize and deserialize SerializedUDPMessage" in {

      val message = SerializedUDPMessage(ByteString("test".getBytes), 1, 1, 1)

      val serialized = serialize(message)

      val deserialized = deserialize(serialized)

      assert(message == deserialized)

      val testBundle = Gossip(TestHelpers.createTestBundle())

      assert(testBundle.event.valid)

      val messages = serializeGrouped(testBundle)

      val messagesSerialized = messages.map(serialize(_))

      val messagesDeserialized: Seq[SerializedUDPMessage] = messagesSerialized.map(deserialize(_).asInstanceOf[SerializedUDPMessage])

      val sorted = messagesDeserialized.sortBy(f => f.packetGroupId).flatMap(_.data).toArray

      val deserializedSorted = deserialize(sorted).asInstanceOf[Gossip[Bundle]]

      assert(testBundle == deserializedSorted)

      assert(deserializedSorted.event.valid)
    }
 */

}
