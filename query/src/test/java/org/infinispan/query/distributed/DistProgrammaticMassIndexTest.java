package org.infinispan.query.distributed;

import static org.testng.AssertJUnit.assertEquals;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.query.CacheQuery;
import org.infinispan.query.Search;
import org.infinispan.query.helper.StaticTestingErrorHandler;
import org.infinispan.query.queries.faceting.Car;
import org.infinispan.query.test.QueryTestSCI;
import org.testng.annotations.Test;

/**
 * Tests verifying that the Mass Indexing for programmatic cache configuration works as well.
 *
 * @author Anna Manukyan
 */
@Test(groups = "functional", testName = "query.distributed.DistProgrammaticMassIndexTest")
public class DistProgrammaticMassIndexTest extends DistributedMassIndexingTest {

   @Override
   protected void createCacheManagers() throws Throwable {
      createCluster(holder -> {
         String defaultName = getClass().getSimpleName();
         holder.getGlobalConfigurationBuilder().defaultCacheName(defaultName).serialization().addContextInitializer(QueryTestSCI.INSTANCE);

         ConfigurationBuilder cacheCfg = getDefaultClusteredCacheConfig(CacheMode.DIST_SYNC, false);
         cacheCfg.indexing()
               .enable()
               .addIndexedEntity(Car.class)
               .addProperty("default.directory_provider", "local-heap")
               .addProperty("error_handler", StaticTestingErrorHandler.class.getName())
               .addProperty("lucene_version", "LUCENE_CURRENT");
         cacheCfg.clustering().stateTransfer().fetchInMemoryState(true);
         holder.newConfigurationBuilder(defaultName).read(cacheCfg.build());
      }, NUM_NODES);
   }

   protected void verifyFindsCar(Cache cache, int count, String carMake) {
      String q = String.format("FROM %s where make:'%s'", Car.class.getName(), carMake);
      CacheQuery<?> cacheQuery = Search.getSearchManager(cache).getQuery(q);

      assertEquals(count, cacheQuery.getResultSize());

      StaticTestingErrorHandler.assertAllGood(cache);
   }

}
