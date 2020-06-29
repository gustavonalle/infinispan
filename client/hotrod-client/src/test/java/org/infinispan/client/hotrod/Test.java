package org.infinispan.client.hotrod;


import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import org.infinispan.client.hotrod.annotation.ClientCacheEntryCreated;
import org.infinispan.client.hotrod.annotation.ClientListener;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.event.ClientCacheEntryCreatedEvent;
import org.infinispan.client.hotrod.impl.ConfigurationProperties;
import org.infinispan.commons.configuration.XMLStringConfiguration;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.commons.marshall.JavaSerializationMarshaller;

public class Test {

   public static void main(String[] args) throws InterruptedException {
      ConfigurationBuilder builder = new ConfigurationBuilder();
      builder.addServer().host("localhost").port(ConfigurationProperties.DEFAULT_HOTROD_PORT).connectionPool()
            .maxActive(20).minIdle(30)
            .marshaller(new JavaSerializationMarshaller())
            .statistics()
            .enable().jmxEnable().security().authentication().username("user").password("user");
      builder.addJavaSerialWhiteList(".*").forceReturnValues(true);

      RemoteCacheManager remoteCacheManager = new RemoteCacheManager(builder.build());

      String cacheConfig = "<infinispan>\n" +
            "   <cache-container>\n" +
            "      <local-cache name=\"cache-consumption-reading\" start=\"EAGER\" module=\"SYNC\" statistics=\"true\">\n" +
            "         <encoding>\n" +
            "            <key media-type=\"application/x-java-object\" />\n" +
            "         </encoding>\n" +
            "      </local-cache>\n" +
            "   </cache-container>\n" +
            "</infinispan>\n";

      remoteCacheManager.administration().createCache("cache-consumption-reading", new XMLStringConfiguration(cacheConfig));

      RemoteCache<Integer, String> remoteCache = remoteCacheManager.getCache("cache-consumption-reading");

//      RemoteCache<Integer, String> decorated = remoteCache.withDataFormat(DataFormat.builder().valueType(MediaType.TEXT_PLAIN).build());

      remoteCache.addClientListener(new EventPrintListener(remoteCache));


      // Read and Write via Java
      remoteCache.put(1024, "value from java");
      // Read it back
      System.out.println(remoteCache.get(1024));

//      remoteCacheManager.stop();
      Thread.sleep(14000);
   }

   @ClientListener
   public static class EventPrintListener {

      private final RemoteCache<Integer, String> cache;

      public EventPrintListener(RemoteCache<Integer, String> cache) {
         this.cache = cache;
      }

      @ClientCacheEntryCreated
      public void handleCreatedEvent(ClientCacheEntryCreatedEvent<Integer> e) {
         System.out.println(e.getKey().getClass());
         CompletableFuture<String> async = cache.getAsync(e.getKey());
         async.whenComplete((s, throwable) -> System.out.println(s));

      }


   }
}
