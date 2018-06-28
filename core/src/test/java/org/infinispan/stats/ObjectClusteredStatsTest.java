package org.infinispan.stats;

import org.infinispan.configuration.cache.StorageType;
import org.infinispan.eviction.EvictionType;
import org.testng.annotations.Test;

/**
 * @author wburns
 * @since JDG 7.2
 */
@Test(groups = "functional", testName = "stats.ObjectClusteredStatsTest")
public class ObjectClusteredStatsTest extends ClusteredStatsTest {
   public ObjectClusteredStatsTest() {
      withStorage(StorageType.OBJECT);
      withEvictionType(EvictionType.COUNT);
   }
}
