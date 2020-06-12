package org.infinispan.client.hotrod;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.impl.transport.tcp.FailoverRequestBalancingStrategy;
import org.infinispan.client.hotrod.impl.transport.tcp.RoundRobinBalancingStrategy;
import org.infinispan.commons.io.ByteBuffer;
import org.infinispan.commons.io.ByteBufferImpl;
import org.infinispan.commons.marshall.AbstractMarshaller;
import org.infinispan.commons.util.CloseableIterator;

public class Reproducer {

   static class PreferredServerBalancingStrategy implements FailoverRequestBalancingStrategy {

      private final InetSocketAddress preferredServer;
      private final RoundRobinBalancingStrategy roundRobinBalancingStrategy = new RoundRobinBalancingStrategy();

      public PreferredServerBalancingStrategy(InetSocketAddress preferredServer) {
         this.preferredServer = preferredServer;
      }

      @Override
      public void setServers(Collection<SocketAddress> servers) {
         roundRobinBalancingStrategy.setServers(servers);
      }

      @Override
      public SocketAddress nextServer(Set<SocketAddress> failedServers) {
         if (failedServers != null && !failedServers.isEmpty() && failedServers.contains(preferredServer)) {
            return roundRobinBalancingStrategy.nextServer(failedServers);
         }
         return preferredServer;
      }
   }
   static class StringMarshaller extends AbstractMarshaller {

      private static final Charset DEFAULT_ENCODING = Charset.forName("UTF-8");

      @Override
      protected ByteBuffer objectToBuffer(Object o, int estimatedSize) {
         byte[] bytes = ((String) o).getBytes(DEFAULT_ENCODING);
         return new ByteBufferImpl(bytes, 0, bytes.length);
      }

      @Override
      public Object objectFromByteBuffer(byte[] buf, int offset, int length) {
         return new String(buf, DEFAULT_ENCODING);
      }

      @Override
      public boolean isMarshallable(Object o) throws Exception {
         return o instanceof String;
      }

   }

   public static void main(String[] args) {
      InetSocketAddress unresolved = InetSocketAddress.createUnresolved("127.0.0.1", 11222);
      ConfigurationBuilder builder = new ConfigurationBuilder();
      builder.addServer().host("127.0.0.1").port(11222);
      builder.marshaller(new StringMarshaller());
//      builder.balancingStrategy(new PreferredServerBalancingStrategy(unresolved));
      RemoteCacheManager cacheManager = new RemoteCacheManager(builder.build());

      RemoteCache cache = cacheManager.getCache("default");



      CacheTopologyInfo cacheTopologyInfo = cache.getCacheTopologyInfo();
      System.out.println(cacheTopologyInfo);
      int numSegments = cacheTopologyInfo.getNumSegments();
      System.out.println("Source has " + numSegments + " segments");


      System.out.println("iterate");
      Set<Integer> segments = new HashSet<>(Arrays.asList(0, 1, 2, 6));
      CloseableIterator<Map.Entry<String, Object>> iterator = cache.retrieveEntries(null, segments, 50000);
      int i = 0;
      while (iterator.hasNext()) {
         Map.Entry<String, Object> e = iterator.next();
         Object entryKey = e.getKey();
         i++;
//         System.out.println(entryKey + ":" + e.getValue());
//         System.out.println(iterator.getClass().getName());
      }
      System.out.println("Retrieved " + i);

      cacheManager.stop();
   }
}
