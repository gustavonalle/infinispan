package org.infinispan.multimap.api;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.multimap.api.embedded.EmbeddedMultimapCacheManagerFactory;
import org.infinispan.multimap.api.embedded.MultimapCacheManager;
import org.infinispan.test.Exceptions;
import org.infinispan.test.SingleCacheManagerTest;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "multimap.EmbeddedMultimapCacheManagerFactoryTest")
public class EmbeddedMultimapCacheManagerFactoryTest extends SingleCacheManagerTest {

   @Override
   protected EmbeddedCacheManager createCacheManager() throws Exception {
      EmbeddedCacheManager cacheManager = TestCacheManagerFactory.createCacheManager(true);
      return cacheManager;
   }

   public void testErrorWhenTxCache() {
      Exceptions.expectException(IllegalStateException.class, () -> {
         MultimapCacheManager multimapCacheManager = EmbeddedMultimapCacheManagerFactory.from(cacheManager);
         ConfigurationBuilder c = getDefaultStandaloneCacheConfig(true);
         multimapCacheManager.defineConfiguration("txCache", c.build());
         multimapCacheManager.get("txCache");
      });
   }
}