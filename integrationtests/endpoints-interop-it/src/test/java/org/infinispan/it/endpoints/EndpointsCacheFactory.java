package org.infinispan.it.endpoints;

import static org.infinispan.client.hotrod.test.HotRodClientTestingUtil.killRemoteCacheManager;
import static org.infinispan.client.hotrod.test.HotRodClientTestingUtil.killServers;
import static org.infinispan.client.hotrod.test.HotRodClientTestingUtil.startHotRodServer;
import static org.infinispan.commons.dataconversion.MediaType.APPLICATION_OBJECT;
import static org.infinispan.test.TestingUtil.killCacheManagers;

import org.apache.commons.httpclient.HttpClient;
import org.infinispan.Cache;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.dataconversion.Encoder;
import org.infinispan.commons.dataconversion.IdentityEncoder;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.commons.dataconversion.TranscoderMarshallerAdapter;
import org.infinispan.commons.marshall.Marshaller;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.configuration.internal.PrivateGlobalConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.marshall.core.EncoderRegistry;
import org.infinispan.protostream.SerializationContextInitializer;
import org.infinispan.rest.RestServer;
import org.infinispan.server.hotrod.HotRodServer;
import org.infinispan.test.fwk.TestCacheManagerFactory;

/**
 * Takes care of construction and destruction of caches, servers and clients for each of the endpoints being tested.
 *
 * @author Galder Zamarreño
 * @since 5.3
 */
public class EndpointsCacheFactory<K, V> {

   private static final int DEFAULT_NUM_OWNERS = 2;

   private EmbeddedCacheManager cacheManager;
   private HotRodServer hotrod;
   private RemoteCacheManager hotrodClient;
   private RestServer rest;

   private Cache<K, V> embeddedCache;
   private RemoteCache<K, V> hotrodCache;
   private HttpClient restClient;

   private final String cacheName;
   private final Marshaller marshaller;
   private final CacheMode cacheMode;
   private final SerializationContextInitializer contextInitializer;
   private final int numOwners;
   private final boolean l1Enable;
   private int restPort;

   EndpointsCacheFactory(CacheMode cacheMode) {
      this(cacheMode, DEFAULT_NUM_OWNERS, false);
   }

   EndpointsCacheFactory(CacheMode cacheMode, SerializationContextInitializer contextInitializer) {
      this("test", null, cacheMode, DEFAULT_NUM_OWNERS, false, null, contextInitializer);
   }

   EndpointsCacheFactory(CacheMode cacheMode, int numOwners, boolean l1Enable) {
      this("test", null, cacheMode, numOwners, l1Enable, null, null);
   }

   public EndpointsCacheFactory(String cacheName, Marshaller marshaller, CacheMode cacheMode) {
      this(cacheName, marshaller, cacheMode, DEFAULT_NUM_OWNERS, null);
   }

   EndpointsCacheFactory(String cacheName, Marshaller marshaller, CacheMode cacheMode, int numOwners, Encoder encoder) {
      this(cacheName, marshaller, cacheMode, numOwners, false, encoder, null);
   }

   EndpointsCacheFactory(String cacheName, Marshaller marshaller, CacheMode cacheMode, int numOwners, boolean l1Enable,
                         Encoder encoder, SerializationContextInitializer contextInitializer) {
      this.cacheName = cacheName;
      this.marshaller = marshaller;
      this.cacheMode = cacheMode;
      this.numOwners = numOwners;
      this.l1Enable = l1Enable;
      this.contextInitializer = contextInitializer;
   }

   public EndpointsCacheFactory<K, V> setup() throws Exception {
      createEmbeddedCache();
      createHotRodCache();
      return this;
   }

   void addRegexWhiteList(String regex) {
      cacheManager.getClassWhiteList().addRegexps(regex);
   }

   private void createEmbeddedCache() {
      GlobalConfigurationBuilder globalBuilder;

      if (cacheMode.isClustered()) {
         globalBuilder = new GlobalConfigurationBuilder();
         globalBuilder.transport().defaultTransport();
      } else {
         globalBuilder = new GlobalConfigurationBuilder().nonClusteredDefault();
      }
      globalBuilder.addModule(PrivateGlobalConfigurationBuilder.class).serverMode(true);
      globalBuilder.defaultCacheName(cacheName);
      if (contextInitializer != null)
         globalBuilder.serialization().addContextInitializer(contextInitializer);

      org.infinispan.configuration.cache.ConfigurationBuilder builder =
            new org.infinispan.configuration.cache.ConfigurationBuilder();
      builder.clustering().cacheMode(cacheMode)
             .encoding().key().mediaType(MediaType.APPLICATION_OBJECT_TYPE)
             .encoding().value().mediaType(MediaType.APPLICATION_OBJECT_TYPE);

      if (cacheMode.isDistributed() && numOwners != DEFAULT_NUM_OWNERS) {
         builder.clustering().hash().numOwners(numOwners);
      }

      if (cacheMode.isDistributed() && l1Enable) {
         builder.clustering().l1().enable();
      }

      cacheManager = cacheMode.isClustered()
            ? TestCacheManagerFactory.createClusteredCacheManager(globalBuilder, builder)
            : TestCacheManagerFactory.createCacheManager(globalBuilder, builder);

      embeddedCache = cacheManager.getCache(cacheName);

      EncoderRegistry encoderRegistry = embeddedCache.getAdvancedCache().getComponentRegistry().getGlobalComponentRegistry().getComponent(EncoderRegistry.class);

      if (marshaller != null) {
         boolean isConversionSupported = encoderRegistry.isConversionSupported(marshaller.mediaType(), APPLICATION_OBJECT);
         if (!isConversionSupported) {
            encoderRegistry.registerTranscoder(new TranscoderMarshallerAdapter(marshaller));
         }
      }
   }

   private void createHotRodCache() {
      createHotRodCache(startHotRodServer(cacheManager));
   }

   private void createHotRodCache(HotRodServer server) {
      hotrod = server;
      hotrodClient = new RemoteCacheManager(new ConfigurationBuilder()
            .addServers("localhost:" + hotrod.getPort())
            .addJavaSerialWhiteList(".*Person.*", ".*CustomEvent.*")
            .marshaller(marshaller)
            .addContextInitializer(contextInitializer)
            .build());
      hotrodCache = cacheName.isEmpty()
            ? hotrodClient.getCache()
            : hotrodClient.getCache(cacheName);
   }

   public static void killCacheFactories(EndpointsCacheFactory... cacheFactories) {
      if (cacheFactories != null) {
         for (EndpointsCacheFactory cacheFactory : cacheFactories) {
            if (cacheFactory != null)
               cacheFactory.teardown();
         }
      }
   }

   void teardown() {
      killRemoteCacheManager(hotrodClient);
      killServers(hotrod);
      killRestServer(rest);
      killCacheManagers(cacheManager);
   }

   private void killRestServer(RestServer rest) {
      if (rest != null) {
         try {
            rest.stop();
         } catch (Exception e) {
            // Ignore
         }
      }
   }

   public Marshaller getMarshaller() {
      return marshaller;
   }

   public Cache<K, V> getEmbeddedCache() {
      return (Cache<K, V>) embeddedCache.getAdvancedCache().withEncoding(IdentityEncoder.class);
   }

   public RemoteCache<K, V> getHotRodCache() {
      return hotrodCache;
   }

   public HttpClient getRestClient() {
      return restClient;
   }

   public String getRestUrl() {
      return String.format("http://localhost:%s/rest/v2/caches/%s", restPort, cacheName);
   }

   HotRodServer getHotrodServer() {
      return hotrod;
   }

}
