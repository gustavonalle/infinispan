package org.infinispan.client.hotrod.impl.iteration;

import static org.testng.Assert.assertEquals;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.infinispan.client.hotrod.CacheTopologyInfo;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.impl.transport.tcp.FailoverRequestBalancingStrategy;
import org.infinispan.client.hotrod.impl.transport.tcp.RoundRobinBalancingStrategy;
import org.infinispan.commons.util.CloseableIterator;
import org.infinispan.query.dsl.embedded.testdomain.hsearch.AccountHS;
import org.infinispan.server.hotrod.HotRodServer;
import org.infinispan.server.hotrod.ServerAddress;
import org.testng.annotations.Test;

/**
 * Test for picking the server to start the iteration where the majority of segments are located.
 */
@Test(groups = "functional", testName = "client.hotrod.iteration.IterationRoutingTest")
public class IterationRoutingTest extends MultiServerDistRemoteIteratorTest {

   @Override
   protected FailoverRequestBalancingStrategy getFailOverStrategy(String host, int port) {
      return new RoundRobinBalancingStrategy();
   }

   @Test
   public void testIterationRouting() {
      for (RemoteCacheManager cacheManager : clients) {
         RemoteCache<Integer, AccountHS> remoteCache = cacheManager.getCache();
         CacheTopologyInfo cacheTopologyInfo = remoteCache.getCacheTopologyInfo();
         Map<SocketAddress, Set<Integer>> segmentsPerServer = cacheTopologyInfo.getSegmentsPerServer();
         segmentsPerServer.forEach((serverAddress, ownedSegments) -> {
            // Trying to retrieve all segments owned by a server should route the iteration to that server
            try (CloseableIterator<Map.Entry<Object, Object>> ignored = remoteCache.retrieveEntries(null, ownedSegments, 10)) {
               assertIterationActiveOnServer((InetSocketAddress) serverAddress);
            }

            assertNoActiveIterations();

            // Trying to retrieve segments owned by 3 servers should route to the server that has more segments
            Set<Integer> mixedSegments = getMajoritySegmentsOwnedBy(fromSocketAddress(serverAddress), cacheTopologyInfo);

            try (CloseableIterator<Map.Entry<Object, Object>> ignored = remoteCache.retrieveEntries(null, mixedSegments, 10)) {
               assertIterationActiveOnServer((InetSocketAddress) serverAddress);
            }
         });
      }
   }

   private Set<Integer> getMajoritySegmentsOwnedBy(ServerAddress majorityServer, CacheTopologyInfo cacheTopologyInfo) {
      Map<SocketAddress, Set<Integer>> segmentsPerServer = cacheTopologyInfo.getSegmentsPerServer();
      Set<Integer> mixedSegments = new HashSet<>();
      int majority = 5;
      int minority = 4;
      for (HotRodServer server : servers) {
         ServerAddress serverAddress = server.getAddress();
         Iterator<Integer> iter = segmentsPerServer.get(fromServerAddress(serverAddress)).iterator();
         int quantity = serverAddress.equals(majorityServer) ? majority : minority;
         for (int i = 0; i < quantity; i++) {
            mixedSegments.add(iter.next());
         }
      }
      return mixedSegments;
   }

   private InetSocketAddress fromServerAddress(ServerAddress address) {
      return InetSocketAddress.createUnresolved(address.getHost(), address.getPort());
   }

   private ServerAddress fromSocketAddress(SocketAddress socketAddress) {
      InetSocketAddress isa = (InetSocketAddress) socketAddress;
      return new ServerAddress(isa.getHostName(), isa.getPort());
   }

   private void assertNoActiveIterations() {
      servers.forEach(h -> assertEquals(0, h.getIterationManager().activeIterations()));
   }

   private void assertIterationActiveOnServer(InetSocketAddress address) {
      for (HotRodServer server : servers) {
         String host = server.getAddress().getHost();
         int port = server.getAddress().getPort();
         int activeIterations = server.getIterationManager().activeIterations();
         if (host.equals(address.getHostName()) && port == address.getPort()) {
            assertEquals(1L, activeIterations);
         } else {
            assertEquals(0L, activeIterations);
         }
      }
   }
}
