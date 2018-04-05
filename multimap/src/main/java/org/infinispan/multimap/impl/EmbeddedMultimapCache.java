package org.infinispan.multimap.impl;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.concurrent.CompletableFuture.supplyAsync;

import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.infinispan.Cache;
import org.infinispan.functional.FunctionalMap;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.functional.impl.FunctionalMapImpl;
import org.infinispan.functional.impl.ReadWriteMapImpl;
import org.infinispan.multimap.api.embedded.MultimapCache;
import org.infinispan.multimap.impl.function.ContainsFunction;
import org.infinispan.multimap.impl.function.GetFunction;
import org.infinispan.multimap.impl.function.PutFunction;
import org.infinispan.multimap.impl.function.RemoveFunction;

/**
 * Embedded implementation of {@link MultimapCache}
 *
 * @author Katia Aresti, karesti@redhat.com
 * @since 9.2
 */
public class EmbeddedMultimapCache<K, V> implements MultimapCache<K, V> {

   private FunctionalMap.ReadWriteMap<K, Collection<V>> readWriteMap;
   private Cache<K, Collection<V>> cache;

   public EmbeddedMultimapCache(Cache<K, Collection<V>> cache) {
      this.cache = cache;
      FunctionalMapImpl<K, Collection<V>> functionalMap = FunctionalMapImpl.create(this.cache.getAdvancedCache());
      this.readWriteMap = ReadWriteMapImpl.create(functionalMap);
   }

   @Override
   public CompletableFuture<Void> put(K key, V value) {
      requireNonNull(key, "key can't be null");
      requireNonNull(value, "value can't be null");
      return readWriteMap.eval(key, new PutFunction<>(value));
   }

   @Override
   public CompletableFuture<Collection<V>> get(K key) {
      requireNonNull(key, "key can't be null");
      return readWriteMap.eval(key, GetFunction.getInstance());
   }

   @Override
   public CompletableFuture<CacheEntry<K, Collection<V>>> getEntry(K key) {
      requireNonNull(key, "key can't be null");
      return supplyAsync(() -> cache.getAdvancedCache().getCacheEntry(key));
   }

   @Override
   public CompletableFuture<Boolean> remove(K key) {
      requireNonNull(key, "key can't be null");
      return readWriteMap.eval(key, RemoveFunction.getRemoveKeyInstance());
   }

   @Override
   public CompletableFuture<Boolean> remove(K key, V value) {
      requireNonNull(key, "key can't be null");
      requireNonNull(value, "value can't be null");
      return readWriteMap.eval(key, new RemoveFunction<>(value));
   }

   @Override
   public CompletableFuture<Void> remove(Predicate<? super V> p) {
      requireNonNull(p, "predicate can't be null");
      return runAsync(() -> this.removeInternal(p));
   }

   @Override
   public CompletableFuture<Boolean> containsKey(K key) {
      requireNonNull(key, "key can't be null");
      return readWriteMap.eval(key, ContainsFunction.getContainsKeyInstance());
   }

   @Override
   public CompletableFuture<Boolean> containsValue(V value) {
      requireNonNull(value, "value can't be null");
      return supplyAsync(() -> containsEntryInternal(value));
   }

   @Override
   public CompletableFuture<Boolean> containsEntry(K key, V value) {
      requireNonNull(key, "key can't be null");
      requireNonNull(value, "value can't be null");
      return readWriteMap.eval(key, new ContainsFunction<>(value));
   }

   @Override
   public CompletableFuture<Long> size() {
      return supplyAsync(() -> sizeInternal());
   }

   private Void removeInternal(Predicate<? super V> p) {
      cache.keySet().stream().forEach((c, key) -> c.computeIfPresent(key, (o, o1) -> {
         Collection<V> values = (Collection<V>) o1;
         Collection<V> newValues = new HashSet<>();
         for (V v : values) {
            if (!p.test(v))
               newValues.add(v);
         }
         return newValues.isEmpty() ? null : newValues;
      }));
      return null;
   }

   private Boolean containsEntryInternal(V value) {
      return cache.entrySet().parallelStream().anyMatch(entry -> entry.getValue().contains(value));
   }

   private Long sizeInternal() {
      return cache.values().parallelStream().mapToLong(Collection::size).sum();
   }

   @Override
   public boolean supportsDuplicates() {
      return false;
   }

   public Cache<K, Collection<V>> getCache() {
      return cache;
   }
}
