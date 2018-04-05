package org.infinispan.multimap.api.embedded;

import org.infinispan.configuration.cache.Configuration;

public interface MultimapCacheManager<K, V> {

   /**
    * Defines a named multimap cache's configuration by using the provided configuration
    * If this cache was already configured either declaritively or programmatically this method will throw a
    * {@link org.infinispan.commons.CacheConfigurationException}.
    *
    * @param name          name of multimap cache whose configuration is being defined
    * @param configuration configuration overrides to use
    * @return a cloned configuration instance
    */
   Configuration defineConfiguration(String name, Configuration configuration);

   /**
    * Retrieves a named multimap cache from the system.
    *
    * @param name, name of multimap cache to retrieve
    * @return null if no configuration exists as per rules set above, otherwise returns a multimap cache instance
    * identified by cacheName
    */
   MultimapCache<K, V> get(String name);
}
