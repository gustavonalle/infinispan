package org.infinispan.lucene;

import java.util.HashMap;
import java.util.Map;

import org.infinispan.commons.marshall.AdvancedExternalizer;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.factories.GlobalComponentRegistry;
import org.infinispan.lifecycle.ModuleLifecycle;
import org.infinispan.lucene.impl.AddOperation;
import org.infinispan.lucene.impl.DeleteOperation;
import org.infinispan.lucene.impl.FileListCacheValue;
import org.infinispan.lucene.impl.FileListCacheValueDelta;

/**
 * Module lifecycle callbacks implementation that enables module specific
 * {@link org.infinispan.commons.marshall.AdvancedExternalizer} implementations to be registered.
 *
 * @author Galder Zamarreño
 * @author Sanne Grinovero
 * @since 5.0
 */
public class LifecycleCallbacks implements ModuleLifecycle {

   @Override
   public void cacheManagerStarting(GlobalComponentRegistry gcr, GlobalConfiguration globalCfg) {
      Map<Integer,AdvancedExternalizer<?>> externalizerMap = globalCfg.serialization().advancedExternalizers();
      externalizerMap.putAll( moduleExternalizers() );
   }

   /**
    * Static helper to allow for explicit registration of Externalizers:
    * service discovery is not always an option.
    * @return the map of Id and Externalizer implementations for this module
    */
   public static Map<Integer,AdvancedExternalizer<?>> moduleExternalizers() {
      Map<Integer,AdvancedExternalizer<?>> externalizerMap = new HashMap<>();
      externalizerMap.put(ExternalizerIds.CHUNK_CACHE_KEY, new ChunkCacheKey.Externalizer());
      externalizerMap.put(ExternalizerIds.FILE_CACHE_KEY, new FileCacheKey.Externalizer());
      externalizerMap.put(ExternalizerIds.FILE_LIST_CACHE_KEY, new FileListCacheKey.Externalizer());
      externalizerMap.put(ExternalizerIds.FILE_METADATA, new FileMetadata.Externalizer());
      externalizerMap.put(ExternalizerIds.FILE_READLOCK_KEY, new FileReadLockKey.Externalizer());
      externalizerMap.put(ExternalizerIds.FILE_LIST_CACHE_VALUE, new FileListCacheValue.Externalizer());
      externalizerMap.put(ExternalizerIds.FILE_LIST_VALUE_DELTA, new FileListCacheValueDelta.Externalizer());
      externalizerMap.put(ExternalizerIds.FILE_LIST_DELTA_ADD, new AddOperation.AddOperationExternalizer());
      externalizerMap.put(ExternalizerIds.FILE_LIST_DELTA_DEL, new DeleteOperation.DeleteElementOperationExternalizer());
      return externalizerMap;
   }

}
