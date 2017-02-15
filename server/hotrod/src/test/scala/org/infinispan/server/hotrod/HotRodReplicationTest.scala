package org.infinispan.server.hotrod

import java.lang.reflect.Method

import org.infinispan.configuration.cache.{CacheMode, ConfigurationBuilder}
import org.infinispan.server.hotrod.OperationStatus._
import org.infinispan.server.hotrod.test.AbstractTestTopologyAwareResponse
import org.infinispan.server.hotrod.test.HotRodTestingUtil._
import org.infinispan.test.AbstractCacheTest._
import org.infinispan.test.TestingUtil
import org.infinispan.topology.ClusterCacheStatus
import org.testng.Assert._
import org.testng.annotations.Test

import scala.collection.JavaConversions._

/**
 * Tests Hot Rod instances configured with replication.
 *
 * @author Galder Zamarreño
 * @since 4.1
 */
@Test(groups = Array("functional"), testName = "server.hotrod.HotRodReplicationTest")
class HotRodReplicationTest extends HotRodMultiNodeTest {

   override protected def cacheName: String = "hotRodReplSync"

   override protected def createCacheConfig: ConfigurationBuilder = {
      val config = hotRodCacheConfiguration(
         getDefaultClusteredCacheConfig(CacheMode.REPL_SYNC, false))
      config.clustering().stateTransfer().fetchInMemoryState(true)
      config
   }

   def testReplicatedPut(m: Method) {
      val resp = clients.head.put(k(m) , 0, 0, v(m))
      assertStatus(resp, Success)
      assertSuccess(clients.tail.head.get(k(m), 0), v(m))
   }

   def testReplicatedPutIfAbsent(m: Method) {
      assertKeyDoesNotExist(clients.head.assertGet(m))
      assertKeyDoesNotExist(clients.tail.head.assertGet(m))
      val resp = clients.head.putIfAbsent(k(m) , 0, 0, v(m))
      assertStatus(resp, Success)
      assertSuccess(clients.tail.head.get(k(m), 0), v(m))
      assertStatus(clients.tail.head.putIfAbsent(k(m) , 0, 0, v(m, "v2-")), OperationNotExecuted)
   }

   def testReplicatedReplace(m: Method) {
      var resp = clients.head.replace(k(m), 0, 0, v(m))
      assertStatus(resp, OperationNotExecuted)
      resp = clients.tail.head.replace(k(m), 0, 0, v(m))
      assertStatus(resp , OperationNotExecuted)
      clients.tail.head.assertPut(m)
      resp = clients.tail.head.replace(k(m), 0, 0, v(m, "v1-"))
      assertStatus(resp, Success)
      assertSuccess(clients.head.assertGet(m), v(m, "v1-"))
      resp = clients.head.replace(k(m), 0, 0, v(m, "v2-"))
      assertStatus(resp, Success)
      assertSuccess(clients.tail.head.assertGet(m), v(m, "v2-"))
   }

   def testPingWithTopologyAwareClient() {
      var resp = clients.head.ping
      assertStatus(resp, Success)
      assertEquals(resp.topologyResponse, null)

      resp = clients.tail.head.ping(1, 0)
      assertStatus(resp, Success)
      assertEquals(resp.topologyResponse, null)

      resp = clients.head.ping(2, 0)
      assertStatus(resp, Success)
      assertTopologyReceived(resp.topologyResponse, servers, currentServerTopologyId)

      resp = clients.tail.head.ping(2, 0)
      assertStatus(resp, Success)
      assertTopologyReceived(resp.topologyResponse, servers, currentServerTopologyId)

      resp = clients.tail.head.ping(2, ClusterCacheStatus.INITIAL_TOPOLOGY_ID + nodeCount)
      assertStatus(resp, Success)
      assertEquals(resp.topologyResponse, null)
   }

   def testReplicatedPutWithTopologyChanges(m: Method) {
      var resp = clients.head.put(k(m) , 0, 0, v(m), 1, 0)
      assertStatus(resp, Success)
      assertEquals(resp.topologyResponse, null)
      assertSuccess(clients.tail.head.get(k(m), 0), v(m))

      resp = clients.head.put(k(m) , 0, 0, v(m, "v1-"), 2, 0)
      assertStatus(resp, Success)
      assertTopologyReceived(resp.topologyResponse, servers, currentServerTopologyId)

      resp = clients.tail.head.put(k(m) , 0, 0, v(m, "v2-"), 2, 0)
      assertStatus(resp, Success)
      assertTopologyReceived(resp.topologyResponse, servers, currentServerTopologyId)

      resp = clients.head.put(k(m) , 0, 0, v(m, "v3-"), 2, ClusterCacheStatus.INITIAL_TOPOLOGY_ID + nodeCount)
      assertStatus(resp, Success)
      assertEquals(resp.topologyResponse, null)
      assertSuccess(clients.tail.head.get(k(m), 0), v(m, "v3-"))

      val newServer = startClusteredServer(servers.tail.head.getPort + 25)
      try {
         val resp = clients.head.put(k(m) , 0, 0, v(m, "v4-"), 2, ClusterCacheStatus.INITIAL_TOPOLOGY_ID + nodeCount)
         assertStatus(resp, Success)
         assertEquals(resp.topologyResponse.topologyId, currentServerTopologyId)
         val topoResp = resp.asTopologyAwareResponse
         assertEquals(topoResp.members.size, 3)
         (newServer.getAddress :: servers.map(_.getAddress)).foreach(
            addr => assertTrue(topoResp.members.exists(_ == addr)))
         assertSuccess(clients.tail.head.get(k(m), 0), v(m, "v4-"))
      } finally {
         stopClusteredServer(newServer)
         TestingUtil.waitForNoRebalance(cache(0, cacheName), cache(1, cacheName))
      }

      resp = clients.head.put(k(m) , 0, 0, v(m, "v5-"), 2, ClusterCacheStatus.INITIAL_TOPOLOGY_ID + nodeCount + 1)
      assertStatus(resp, Success)
      assertEquals(resp.topologyResponse.topologyId, currentServerTopologyId)
      var topoResp = resp.asTopologyAwareResponse
      assertEquals(topoResp.members.size, 2)
      servers.map(_.getAddress).foreach(
         addr => assertTrue(topoResp.members.exists(_ == addr)))
      assertSuccess(clients.tail.head.get(k(m), 0), v(m, "v5-"))

      val crashingServer = startClusteredServer(
            servers.tail.head.getPort + 25, doCrash = true)
      try {
         val resp = clients.head.put(k(m) , 0, 0, v(m, "v6-"), 2, ClusterCacheStatus.INITIAL_TOPOLOGY_ID + nodeCount + 2)
         assertStatus(resp, Success)
         assertEquals(resp.topologyResponse.topologyId, currentServerTopologyId)
         val topoResp = resp.asTopologyAwareResponse
         assertEquals(topoResp.members.size, 3)
         (crashingServer.getAddress :: servers.map(_.getAddress)).foreach(
            addr => assertTrue(topoResp.members.exists(_ == addr)))
         assertSuccess(clients.tail.head.get(k(m), 0), v(m, "v6-"))
      } finally {
         stopClusteredServer(crashingServer)
         TestingUtil.waitForNoRebalance(cache(0, cacheName), cache(1, cacheName))
      }

      resp = clients.head.put(k(m) , 0, 0, v(m, "v7-"), 2, ClusterCacheStatus.INITIAL_TOPOLOGY_ID + nodeCount + 3)
      assertStatus(resp, Success)
      assertEquals(resp.topologyResponse.topologyId, currentServerTopologyId)
      topoResp = resp.asTopologyAwareResponse
      assertEquals(topoResp.members.size, 2)
      servers.map(_.getAddress).foreach(
         addr => assertTrue(topoResp.members.exists(_ == addr)))
      assertSuccess(clients.tail.head.get(k(m), 0), v(m, "v7-"))

      resp = clients.head.put(k(m) , 0, 0, v(m, "v8-"), 3, 1)
      assertStatus(resp, Success)

      checkTopologyReceived(resp.topologyResponse, servers, cacheName)
      assertSuccess(clients.tail.head.get(k(m), 0), v(m, "v8-"))
   }

   def testSize(m: Method): Unit = {
      // Cache contents not cleared between methods to avoid deleting
      // topology information, so just use a different cache
      val cacheName = "repl-size"
      defineCaches(cacheName)
      val clients = createClients(cacheName)
      val sizeStart = clients.head.size()
      assertStatus(sizeStart, Success)
      assertEquals(0, sizeStart.size)
      for (i <- 0 until 20) clients.tail.head.assertPut(m, s"k-$i", s"v-$i")
      val sizeEnd = clients.tail.head.size()
      assertStatus(sizeEnd, Success)
      assertEquals(20, sizeEnd.size)
   }

   @Test(enabled=false) // Disable explicitly to avoid TestNG thinking this is a test!!
   protected def checkTopologyReceived(topoResp: AbstractTestTopologyAwareResponse,
           servers: List[HotRodServer], cacheName: String) {
      assertHashTopology20Received(topoResp, servers, cacheName, currentServerTopologyId)
   }

}
