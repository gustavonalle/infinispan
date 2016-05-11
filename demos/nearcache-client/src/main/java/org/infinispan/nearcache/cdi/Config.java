package org.infinispan.nearcache.cdi;

import org.infinispan.cdi.embedded.ConfigureCache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.eviction.EvictionStrategy;
import org.infinispan.nearcache.jms.RemoteEventStoreConfigurationBuilder;

import javax.enterprise.inject.Produces;

/**
 * Configuration of the cache
 *
 * @author Galder Zamarreño
 * @since 5.1
 */
public class Config {

   @AddressCache
   @ConfigureCache("address-cache")
   @Produces
   public Configuration addressCache() {
      return new ConfigurationBuilder()
         .eviction().strategy(EvictionStrategy.LRU).maxEntries(4)
         .persistence().addStore(RemoteEventStoreConfigurationBuilder.class).shared(true)
         .build();
   }

}
