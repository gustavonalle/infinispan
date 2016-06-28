package org.infinispan.commons.api;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * AsyncCache. This interface is implemented by caches which support asynchronous variants of the various
 * put/get/remove/clear/replace/putAll methods
 *
 * Note that these methods only really make sense if you are using a clustered cache.  I.e., when used in LOCAL mode,
 * these "async" operations offer no benefit whatsoever.  These methods, such as {@link #putAsync(Object, Object)}
 * offer the best of both worlds between a fully synchronous and a fully asynchronous cache in that a
 * {@link CompletableFuture} is returned.  The <tt>NotifyingFuture</tt> can then be ignored or thrown away for typical
 * asynchronous behaviour, or queried for synchronous behaviour, which would block until any remote calls complete.
 * Note that all remote calls are, as far as the transport is concerned, synchronous.  This allows you the guarantees
 * that remote calls succeed, while not blocking your application thread unnecessarily.  For example, usage such as
 * the following could benefit from the async operations:
 * <pre>
 *   NotifyingFuture f1 = cache.putAsync("key1", "value1");
 *   NotifyingFuture f2 = cache.putAsync("key2", "value2");
 *   NotifyingFuture f3 = cache.putAsync("key3", "value3");
 *   f1.get();
 *   f2.get();
 *   f3.get();
 * </pre>
 * The net result is behavior similar to synchronous RPC calls in that at the end, you have guarantees that all calls
 * completed successfully, but you have the added benefit that the three calls could happen in parallel.  This is
 * especially advantageous if the cache uses distribution and the three keys map to different cache instances in the
 * cluster.
 * <p/>
 * Also, the use of async operations when within a transaction return your local value only, as expected.  A
 * NotifyingFuture is still returned though for API consistency.
 * <p/>
 *
 * @author Mircea Markus
 * @author Manik Surtani
 * @author Galder Zamarreño
 * @author Tristan Tarrant
 * @since 6.0
 */
public interface AsyncCache<K, V> {
   /**
    * Asynchronous version of {@link BasicCache#put(Object, Object)}.  This method does not block on remote calls, even if your
    * cache mode is synchronous.  Has no benefit over {@link BasicCache#put(Object, Object)} if used in LOCAL mode.
    * <p/>
    *
    * @param key   key to use
    * @param value value to store
    * @return a future containing the old value replaced.
    */
   CompletableFuture<V> putAsync(K key, V value);

   /**
    * Asynchronous version of {@link BasicCache#put(Object, Object, long, TimeUnit)} .  This method does not block on remote
    * calls, even if your cache mode is synchronous.  Has no benefit over {@link BasicCache#put(Object, Object, long, TimeUnit)}
    * if used in LOCAL mode.
    *
    * @param key      key to use
    * @param value    value to store
    * @param lifespan lifespan of entry
    * @param unit     time unit for lifespan
    * @return a future containing the old value replaced
    */
   CompletableFuture<V> putAsync(K key, V value, long lifespan, TimeUnit unit);

   /**
    * Asynchronous version of {@link BasicCache#put(Object, Object, long, TimeUnit, long, TimeUnit)}.  This method does not block
    * on remote calls, even if your cache mode is synchronous.  Has no benefit over {@link BasicCache#put(Object, Object, long,
    * TimeUnit, long, TimeUnit)} if used in LOCAL mode.
    *
    * @param key          key to use
    * @param value        value to store
    * @param lifespan     lifespan of entry
    * @param lifespanUnit time unit for lifespan
    * @param maxIdle      the maximum amount of time this key is allowed to be idle for before it is considered as
    *                     expired
    * @param maxIdleUnit  time unit for max idle time
    * @return a future containing the old value replaced
    */
   CompletableFuture<V> putAsync(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdle, TimeUnit maxIdleUnit);

   /**
    * Asynchronous version of {@link BasicCache#putAll(Map)}.  This method does not block on remote calls, even if your cache mode
    * is synchronous.  Has no benefit over {@link BasicCache#putAll(Map)} if used in LOCAL mode.
    *
    * @param data to store
    * @return a future containing a void return type
    */
   CompletableFuture<Void> putAllAsync(Map<? extends K, ? extends V> data);

   /**
    * Asynchronous version of {@link BasicCache#putAll(Map, long, TimeUnit)}.  This method does not block on remote calls, even if
    * your cache mode is synchronous.  Has no benefit over {@link BasicCache#putAll(Map, long, TimeUnit)} if used in LOCAL mode.
    *
    * @param data     to store
    * @param lifespan lifespan of entry
    * @param unit     time unit for lifespan
    * @return a future containing a void return type
    */
   CompletableFuture<Void> putAllAsync(Map<? extends K, ? extends V> data, long lifespan, TimeUnit unit);

   /**
    * Asynchronous version of {@link BasicCache#putAll(Map, long, TimeUnit, long, TimeUnit)}.  This method does not block on
    * remote calls, even if your cache mode is synchronous.  Has no benefit over {@link BasicCache#putAll(Map, long, TimeUnit,
    * long, TimeUnit)} if used in LOCAL mode.
    *
    * @param data         to store
    * @param lifespan     lifespan of entry
    * @param lifespanUnit time unit for lifespan
    * @param maxIdle      the maximum amount of time this key is allowed to be idle for before it is considered as
    *                     expired
    * @param maxIdleUnit  time unit for max idle time
    * @return a future containing a void return type
    */
   CompletableFuture<Void> putAllAsync(Map<? extends K, ? extends V> data, long lifespan, TimeUnit lifespanUnit, long maxIdle, TimeUnit maxIdleUnit);

   /**
    * Asynchronous version of {@link BasicCache#clear()}.  This method does not block on remote calls, even if your cache mode is
    * synchronous.  Has no benefit over {@link BasicCache#clear()} if used in LOCAL mode.
    *
    * @return a future containing a void return type
    */
   CompletableFuture<Void> clearAsync();

   /**
    * Asynchronous version of {@link BasicCache#putIfAbsent(Object, Object)}.  This method does not block on remote calls, even if
    * your cache mode is synchronous.  Has no benefit over {@link BasicCache#putIfAbsent(Object, Object)} if used in LOCAL mode.
    * <p/>
    *
    * @param key   key to use
    * @param value value to store
    * @return a future containing the old value replaced.
    */
   CompletableFuture<V> putIfAbsentAsync(K key, V value);

   /**
    * Asynchronous version of {@link BasicCache#putIfAbsent(Object, Object, long, TimeUnit)} .  This method does not block on
    * remote calls, even if your cache mode is synchronous.  Has no benefit over {@link BasicCache#putIfAbsent(Object, Object,
    * long, TimeUnit)} if used in LOCAL mode.
    *
    * @param key      key to use
    * @param value    value to store
    * @param lifespan lifespan of entry
    * @param unit     time unit for lifespan
    * @return a future containing the old value replaced
    */
   CompletableFuture<V> putIfAbsentAsync(K key, V value, long lifespan, TimeUnit unit);

   /**
    * Asynchronous version of {@link BasicCache#putIfAbsent(Object, Object, long, TimeUnit, long, TimeUnit)}.  This method does
    * not block on remote calls, even if your cache mode is synchronous.  Has no benefit over {@link
    * BasicCache#putIfAbsent(Object, Object, long, TimeUnit, long, TimeUnit)} if used in LOCAL mode.
    *
    * @param key          key to use
    * @param value        value to store
    * @param lifespan     lifespan of entry
    * @param lifespanUnit time unit for lifespan
    * @param maxIdle      the maximum amount of time this key is allowed to be idle for before it is considered as
    *                     expired
    * @param maxIdleUnit  time unit for max idle time
    * @return a future containing the old value replaced
    */
   CompletableFuture<V> putIfAbsentAsync(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdle, TimeUnit maxIdleUnit);

   /**
    * Asynchronous version of {@link BasicCache#remove(Object)}.  This method does not block on remote calls, even if your cache
    * mode is synchronous.  Has no benefit over {@link BasicCache#remove(Object)} if used in LOCAL mode.
    *
    * @param key key to remove
    * @return a future containing the value removed
    */
   CompletableFuture<V> removeAsync(Object key);

   /**
    * Asynchronous version of {@link BasicCache#remove(Object, Object)}.  This method does not block on remote calls, even if your
    * cache mode is synchronous.  Has no benefit over {@link BasicCache#remove(Object, Object)} if used in LOCAL mode.
    *
    * @param key   key to remove
    * @param value value to match on
    * @return a future containing a boolean, indicating whether the entry was removed or not
    */
   CompletableFuture<Boolean> removeAsync(Object key, Object value);

   /**
    * Asynchronous version of {@link BasicCache#replace(Object, Object)}.  This method does not block on remote calls, even if
    * your cache mode is synchronous.  Has no benefit over {@link BasicCache#replace(Object, Object)} if used in LOCAL mode.
    *
    * @param key   key to remove
    * @param value value to store
    * @return a future containing the previous value overwritten
    */
   CompletableFuture<V> replaceAsync(K key, V value);

   /**
    * Asynchronous version of {@link BasicCache#replace(Object, Object, long, TimeUnit)}.  This method does not block on remote
    * calls, even if your cache mode is synchronous.  Has no benefit over {@link BasicCache#replace(Object, Object, long,
    * TimeUnit)} if used in LOCAL mode.
    *
    * @param key      key to remove
    * @param value    value to store
    * @param lifespan lifespan of entry
    * @param unit     time unit for lifespan
    * @return a future containing the previous value overwritten
    */
   CompletableFuture<V> replaceAsync(K key, V value, long lifespan, TimeUnit unit);

   /**
    * Asynchronous version of {@link BasicCache#replace(Object, Object, long, TimeUnit, long, TimeUnit)}.  This method does not
    * block on remote calls, even if your cache mode is synchronous.  Has no benefit over {@link BasicCache#replace(Object,
    * Object, long, TimeUnit, long, TimeUnit)} if used in LOCAL mode.
    *
    * @param key          key to remove
    * @param value        value to store
    * @param lifespan     lifespan of entry
    * @param lifespanUnit time unit for lifespan
    * @param maxIdle      the maximum amount of time this key is allowed to be idle for before it is considered as
    *                     expired
    * @param maxIdleUnit  time unit for max idle time
    * @return a future containing the previous value overwritten
    */
   CompletableFuture<V> replaceAsync(K key, V value, long lifespan, TimeUnit lifespanUnit, long maxIdle, TimeUnit maxIdleUnit);

   /**
    * Asynchronous version of {@link BasicCache#replace(Object, Object, Object)}.  This method does not block on remote calls,
    * even if your cache mode is synchronous.  Has no benefit over {@link BasicCache#replace(Object, Object, Object)} if used in
    * LOCAL mode.
    *
    * @param key      key to remove
    * @param oldValue value to overwrite
    * @param newValue value to store
    * @return a future containing a boolean, indicating whether the entry was replaced or not
    */
   CompletableFuture<Boolean> replaceAsync(K key, V oldValue, V newValue);

   /**
    * Asynchronous version of {@link BasicCache#replace(Object, Object, Object, long, TimeUnit)}.  This method does not block on
    * remote calls, even if your cache mode is synchronous.  Has no benefit over {@link BasicCache#replace(Object, Object, Object,
    * long, TimeUnit)} if used in LOCAL mode.
    *
    * @param key      key to remove
    * @param oldValue value to overwrite
    * @param newValue value to store
    * @param lifespan lifespan of entry
    * @param unit     time unit for lifespan
    * @return a future containing a boolean, indicating whether the entry was replaced or not
    */
   CompletableFuture<Boolean> replaceAsync(K key, V oldValue, V newValue, long lifespan, TimeUnit unit);

   /**
    * Asynchronous version of {@link BasicCache#replace(Object, Object, Object, long, TimeUnit, long, TimeUnit)}.  This method
    * does not block on remote calls, even if your cache mode is synchronous.  Has no benefit over {@link
    * BasicCache#replace(Object, Object, Object, long, TimeUnit, long, TimeUnit)} if used in LOCAL mode.
    *
    * @param key          key to remove
    * @param oldValue     value to overwrite
    * @param newValue     value to store
    * @param lifespan     lifespan of entry
    * @param lifespanUnit time unit for lifespan
    * @param maxIdle      the maximum amount of time this key is allowed to be idle for before it is considered as
    *                     expired
    * @param maxIdleUnit  time unit for max idle time
    * @return a future containing a boolean, indicating whether the entry was replaced or not
    */
   CompletableFuture<Boolean> replaceAsync(K key, V oldValue, V newValue, long lifespan, TimeUnit lifespanUnit, long maxIdle, TimeUnit maxIdleUnit);

   /**
    * Asynchronous version of {@link BasicCache#get(Object)} that allows user code to
    * retrieve the value associated with a key at a later stage, hence allowing
    * multiple parallel get requests to be sent. Normally, when this method
    * detects that the value is likely to be retrieved from from a remote
    * entity, it will span a different thread in order to allow the
    * asynchronous get call to return immediately. If the call will definitely
    * resolve locally, for example when the cache is configured with LOCAL mode
    * and no stores are configured, the get asynchronous call will act
    * sequentially and will have no different to {@link BasicCache#get(Object)}.
    *
    * @param key key to retrieve
    * @return a future that can be used to retrieve value associated with the
    * key when this is available. The actual value returned by the future
    * follows the same rules as {@link BasicCache#get(Object)}
    */
   CompletableFuture<V> getAsync(K key);
}
