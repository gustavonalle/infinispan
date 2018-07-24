package org.infinispan.client.hotrod.impl;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.infinispan.client.hotrod.Flag;
import org.infinispan.client.hotrod.MetadataValue;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.VersionedValue;
import org.infinispan.client.hotrod.logging.Log;
import org.infinispan.client.hotrod.logging.LogFactory;
import org.infinispan.client.hotrod.near.NearCacheService;

/**
 * Near {@link org.infinispan.client.hotrod.RemoteCache} implementation enabling
 *
 * @param <K>
 * @param <V>
 */
public class InvalidatedNearRemoteCache<K, V> extends RemoteCacheImpl<K, V> {
   private static final Log log = LogFactory.getLog(InvalidatedNearRemoteCache.class);
   private final NearCacheService<K, V> nearcache;

   public InvalidatedNearRemoteCache(RemoteCacheManager rcm, String name, NearCacheService<K, V> nearcache) {
      super(rcm, name);
      this.nearcache = nearcache;
   }

   @Override
   public V get(Object key) {
      VersionedValue<V> versioned = getVersioned((K) key);
      return versioned != null ? versioned.getValue() : null;
   }

   @Override
   public VersionedValue<V> getVersioned(K key) {
      VersionedValue<V> nearValue = nearcache.get(key);
      if (nearValue == null) {
         MetadataValue<V> remoteValue = super.getWithMetadata(key);
         if (remoteValue != null)
            nearcache.putIfAbsent(key, remoteValue);

         return remoteValue;
      }

      return nearValue;
   }

   @Override
   public MetadataValue<V> getWithMetadata(K key) {
      MetadataValue<V> nearValue = nearcache.get(key);
      if (nearValue == null) {
         MetadataValue<V> remoteValue = super.getWithMetadata(key);
         if (remoteValue != null) {
            nearcache.putIfAbsent(key, remoteValue);
            if (remoteValue.getMaxIdle() > 0) {
               log.nearCacheMaxIdleUnsupported();
            }
         }
         return remoteValue;
      } else {
         return nearValue;
      }
   }

   @Override
   public V put(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdleTime, TimeUnit maxIdleTimeUnit) {
      V ret = super.put(key, value, lifespan, lifespanUnit, maxIdleTime, maxIdleTimeUnit);
      nearcache.remove(key); // Eager invalidation to avoid race
      return ret;
   }

   @Override
   public void putAll(Map<? extends K, ? extends V> map, long lifespan, TimeUnit lifespanUnit, long maxIdleTime, TimeUnit maxIdleTimeUnit) {
      super.putAll(map, lifespan, lifespanUnit, maxIdleTime, maxIdleTimeUnit);
      map.keySet().forEach(nearcache::remove);
   }

   @Override
   public V replace(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdleTime, TimeUnit maxIdleTimeUnit) {
      boolean hasForceReturnValue = operationsFactory.hasFlag(Flag.FORCE_RETURN_VALUE);
      V prev = super.replace(key, value, lifespan, lifespanUnit, maxIdleTime, maxIdleTimeUnit);
      invalidateNearCacheIfNeeded(hasForceReturnValue, key, prev);
      return prev;
   }

   @Override
   public boolean replaceWithVersion(K key, V newValue, long version, long lifespan, TimeUnit lifespanTimeUnit, long maxIdle, TimeUnit maxIdleTimeUnit) {
      boolean replaced = super.replaceWithVersion(key, newValue, version, lifespan, lifespanTimeUnit, maxIdle, maxIdleTimeUnit);
      if (replaced) nearcache.remove(key);
      return replaced;
   }

   @Override
   public V remove(Object key) {
      boolean hasForceReturnValue = operationsFactory.hasFlag(Flag.FORCE_RETURN_VALUE);
      V prev = super.remove(key);
      invalidateNearCacheIfNeeded(hasForceReturnValue, key, prev);
      return prev;
   }

   @Override
   public boolean removeWithVersion(K key, long version) {
      boolean removed = super.removeWithVersion(key, version);
      if (removed) nearcache.remove(key); // Eager invalidation to avoid race
      return removed;
   }

   @Override
   public void clear() {
      super.clear();
      nearcache.clear(); // Clear near cache too
   }

   @SuppressWarnings("unchecked")
   void invalidateNearCacheIfNeeded(boolean hasForceReturnValue, Object key, Object prev) {
      if (!hasForceReturnValue || prev != null)
         nearcache.remove((K) key);
   }

   @Override
   public void start() {
      nearcache.start(this);
   }

   @Override
   public void stop() {
      nearcache.stop(this);
   }
}
