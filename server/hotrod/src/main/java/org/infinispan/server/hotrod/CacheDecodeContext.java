package org.infinispan.server.hotrod;

import static java.lang.String.format;
import static org.infinispan.server.hotrod.Response.createEmptyResponse;
import static org.infinispan.util.concurrent.CompletableFutures.extractException;

import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.security.auth.Subject;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.container.versioning.EntryVersion;
import org.infinispan.container.versioning.NumericVersion;
import org.infinispan.container.versioning.NumericVersionGenerator;
import org.infinispan.container.versioning.SimpleClusteredVersion;
import org.infinispan.container.versioning.VersionGenerator;
import org.infinispan.context.Flag;
import org.infinispan.counter.EmbeddedCounterManagerFactory;
import org.infinispan.counter.api.CounterConfiguration;
import org.infinispan.counter.api.CounterManager;
import org.infinispan.counter.api.StrongCounter;
import org.infinispan.counter.api.WeakCounter;
import org.infinispan.counter.exception.CounterOutOfBoundsException;
import org.infinispan.counter.impl.CounterModuleLifecycle;
import org.infinispan.counter.impl.manager.EmbeddedCounterManager;
import org.infinispan.factories.ComponentRegistry;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.metadata.EmbeddedMetadata;
import org.infinispan.metadata.Metadata;
import org.infinispan.registry.InternalCacheRegistry;
import org.infinispan.remoting.rpc.RpcManager;
import org.infinispan.security.Security;
import org.infinispan.server.core.ServerConstants;
import org.infinispan.server.hotrod.counter.CounterAddDecodeContext;
import org.infinispan.server.hotrod.counter.CounterCompareAndSetDecodeContext;
import org.infinispan.server.hotrod.counter.CounterCreateDecodeContext;
import org.infinispan.server.hotrod.counter.CounterListenerDecodeContext;
import org.infinispan.server.hotrod.counter.listener.ClientCounterManagerNotificationManager;
import org.infinispan.server.hotrod.counter.listener.ListenerOperationStatus;
import org.infinispan.server.hotrod.counter.response.CounterConfigurationResponse;
import org.infinispan.server.hotrod.counter.response.CounterNamesResponse;
import org.infinispan.server.hotrod.counter.response.CounterValueResponse;
import org.infinispan.server.hotrod.logging.Log;
import org.infinispan.util.logging.LogFactory;

import io.netty.channel.Channel;

/**
 * Invokes operations against the cache based on the state kept during decoding process
 */
public final class CacheDecodeContext {
   static final long MillisecondsIn30days = TimeUnit.DAYS.toMillis(30);
   static final Log log = LogFactory.getLog(CacheDecodeContext.class, Log.class);
   static final boolean isTrace = log.isTraceEnabled();

   private final HotRodServer server;

   CacheDecodeContext(HotRodServer server) {
      this.server = server;
   }

   private CounterManager counterManager;
   VersionedDecoder decoder;
   HotRodHeader header;
   AdvancedCache<byte[], byte[]> cache;
   byte[] key;
   RequestParameters params;
   Object operationDecodeContext;
   Subject subject;

   public HotRodHeader getHeader() {
      return header;
   }

   public byte[] getKey() {
      return key;
   }

   public RequestParameters getParams() {
      return params;
   }

   Response removeCounterListener(HotRodServer server) {
      ClientCounterManagerNotificationManager notificationManager = server.getClientCounterNotificationManager();
      CounterListenerDecodeContext opCtx = operationContext();
      return createResponseFrom(
            notificationManager.removeCounterListener(opCtx.getListenerId(), opCtx.getCounterName()));
   }

   Response addCounterListener(HotRodServer server, Channel channel) {
      ClientCounterManagerNotificationManager notificationManager = server.getClientCounterNotificationManager();
      CounterListenerDecodeContext opCtx = operationContext();
      return createResponseFrom(notificationManager
            .addCounterListener(opCtx.getListenerId(), header.getVersion(), opCtx.getCounterName(), channel));
   }

   Response getCounterNames() {
      return new CounterNamesResponse(header, counterManager.getCounterNames());
   }

   Response counterRemove() {
      String counterName = operationContext();
      counterManager.remove(counterName);
      return createEmptyResponse(header, OperationStatus.Success);
   }

   void counterCompareAndSwap(Consumer<Response> sendResponse) {
      CounterCompareAndSetDecodeContext decodeContext = operationContext();
      final long expect = decodeContext.getExpected();
      final long update = decodeContext.getUpdate();
      final String name = decodeContext.getCounterName();

      applyCounter(name, sendResponse,
            (counter, responseConsumer) -> counter.compareAndSwap(expect, update)
                  .whenComplete(longResultHandler(responseConsumer)),
            (counter, responseConsumer) -> responseConsumer
                  .accept(createExceptionResponse(log.invalidWeakCounter(name)))
      );
   }

   void counterGet(Consumer<Response> sendResponse) {
      applyCounter(operationContext(), sendResponse,
            (counter, responseConsumer) -> counter.getValue().whenComplete(longResultHandler(responseConsumer)),
            (counter, responseConsumer) -> longResultHandler(responseConsumer).accept(counter.getValue(), null)
      );
   }

   void counterReset(Consumer<Response> sendResponse) {
      applyCounter(operationContext(), sendResponse,
            (counter, responseConsumer) -> counter.reset().whenComplete(voidResultHandler(responseConsumer)),
            (counter, responseConsumer) -> counter.reset().whenComplete(voidResultHandler(responseConsumer))
      );
   }

   void counterAddAndGet(Consumer<Response> sendResponse) {
      CounterAddDecodeContext decodeContext = operationContext();
      final long value = decodeContext.getValue();
      applyCounter(decodeContext.getCounterName(), sendResponse,
            (counter, responseConsumer) -> counter.addAndGet(value).whenComplete(longResultHandler(responseConsumer)),
            (counter, responseConsumer) -> counter.add(value)
                  .whenComplete((aVoid, throwable) -> longResultHandler(responseConsumer).accept(0L, throwable))
      );
   }

   void getCounterConfiguration(Consumer<Response> sendResponse) {
      ((EmbeddedCounterManager) counterManager).getConfigurationAsync(operationContext())
            .whenComplete((configuration, throwable) -> {
               if (throwable != null) {
                  checkCounterThrowable(sendResponse, throwable);
               } else {
                  sendResponse.accept(configuration == null ? missingCounterResponse()
                                                            : new CounterConfigurationResponse(header, configuration));
               }
            });
   }

   void isCounterDefined(Consumer<Response> sendResponse) {
      ((EmbeddedCounterManager) counterManager).isDefinedAsync(operationContext())
            .whenComplete(booleanResultHandler(sendResponse));
   }

   void createCounter(Consumer<Response> sendResponse) {
      CounterCreateDecodeContext decodeContext = operationContext();
      ((EmbeddedCounterManager) counterManager)
            .defineCounterAsync(decodeContext.getCounterName(), decodeContext.getConfiguration())
            .whenComplete(booleanResultHandler(sendResponse));
   }

   <T> T operationContext(Supplier<T> constructor) {
      T opCtx = operationContext();
      if (opCtx == null) {
         opCtx = constructor.get();
         operationDecodeContext = opCtx;
         return opCtx;
      } else {
         return opCtx;
      }
   }

   <T> T operationContext() {
      //noinspection unchecked
      return (T) operationDecodeContext;
   }

   private void checkCounterThrowable(Consumer<Response> send, Throwable throwable) {
      Throwable cause = extractException(throwable);
      if (cause instanceof CounterOutOfBoundsException) {
         send.accept(createEmptyResponse(header, OperationStatus.NotExecutedWithPrevious));
      } else {
         send.accept(createExceptionResponse(cause));
      }
   }

   private Response createCounterBooleanResponse(boolean result) {
      return createEmptyResponse(header, result ? OperationStatus.Success : OperationStatus.OperationNotExecuted);
   }

   private Response missingCounterResponse() {
      return createEmptyResponse(header, OperationStatus.KeyDoesNotExist);
   }

   private BiConsumer<Boolean, Throwable> booleanResultHandler(Consumer<Response> sendResponse) {
      return (aBoolean, throwable) -> {
         if (throwable != null) {
            checkCounterThrowable(sendResponse, throwable);
         } else {
            sendResponse.accept(createCounterBooleanResponse(aBoolean));
         }
      };
   }

   private BiConsumer<Long, Throwable> longResultHandler(Consumer<Response> sendResponse) {
      return (value, throwable) -> {
         if (throwable != null) {
            checkCounterThrowable(sendResponse, throwable);
         } else {
            sendResponse.accept(new CounterValueResponse(header, value));
         }
      };
   }


   ErrorResponse createExceptionResponse(Throwable e) {
      if (e instanceof InvalidMagicIdException) {
         log.exceptionReported(e);
         return new ErrorResponse((byte) 0, 0, "", (short) 1, OperationStatus.InvalidMagicOrMsgId, 0, e.toString());
      } else if (e instanceof HotRodUnknownOperationException) {
         log.exceptionReported(e);
         HotRodUnknownOperationException hruoe = (HotRodUnknownOperationException) e;
         return new ErrorResponse(hruoe.version, hruoe.messageId, "", (short) 1, OperationStatus.UnknownOperation, 0, e.toString());
      } else if (e instanceof UnknownVersionException) {
         log.exceptionReported(e);
         UnknownVersionException uve = (UnknownVersionException) e;
         return new ErrorResponse(uve.version, uve.messageId, "", (short) 1, OperationStatus.UnknownVersion, 0, e.toString());
      } else if (e instanceof RequestParsingException) {
         if (e instanceof CacheNotFoundException)
            log.debug(e.getMessage());
         else
            log.exceptionReported(e);

         String msg = e.getCause() == null ? e.toString() : format("%s: %s", e.getMessage(), e.getCause().toString());
         RequestParsingException rpe = (RequestParsingException) e;
         return new ErrorResponse(rpe.version, rpe.messageId, "", (short) 1, OperationStatus.ParseError, 0, msg);
      } else if (e instanceof IllegalStateException) {
         // Some internal server code could throw this, so make sure it's logged
         log.exceptionReported(e);
         return decoder.createErrorResponse(header, e);
      } else if (decoder != null) {
         return decoder.createErrorResponse(header, e);
      } else {
         log.exceptionReported(e);
         return new ErrorResponse((byte) 0, 0, "", (short) 1, OperationStatus.ServerError, 1, e.toString());
      }
   }

   Response replace() {
      // Avoid listener notification for a simple optimization
      // on whether a new version should be calculated or not.
      byte[] prev = cache.withFlags(Flag.SKIP_LISTENER_NOTIFICATION).get(key);
      if (prev != null) {
         // Generate new version only if key present
         prev = cache.replace(key, (byte[]) operationDecodeContext, buildMetadata());
      }
      if (prev != null)
         return successResp(prev);
      else
         return notExecutedResp(null);
   }

   void obtainCache(EmbeddedCacheManager cacheManager) throws RequestParsingException {
      switch (header.op) {
         case COUNTER_CREATE:
         case COUNTER_ADD_AND_GET:
         case COUNTER_ADD_LISTENER:
         case COUNTER_CAS:
         case COUNTER_GET:
         case COUNTER_RESET:
         case COUNTER_IS_DEFINED:
         case COUNTER_REMOVE_LISTENER:
         case COUNTER_GET_CONFIGURATION:
         case COUNTER_REMOVE:
         case COUNTER_GET_NAMES:
            header.cacheName = CounterModuleLifecycle.COUNTER_CACHE_NAME;
            this.counterManager = EmbeddedCounterManagerFactory.asCounterManager(cacheManager);
            return;
      }
      String cacheName = header.cacheName;
      // Try to avoid calling cacheManager.getCacheNames() if possible, since this creates a lot of unnecessary garbage
      AdvancedCache<byte[], byte[]> cache = server.getKnownCache(cacheName);
      if (cache == null) {
         // Talking to the wrong cache are really request parsing errors
         // and hence should be treated as client errors
         InternalCacheRegistry icr = cacheManager.getGlobalComponentRegistry().getComponent(InternalCacheRegistry.class);
         if (icr.isPrivateCache(cacheName)) {
            throw new RequestParsingException(
                  format("Remote requests are not allowed to private caches. Do no send remote requests to cache '%s'", cacheName),
                  header.version, header.messageId);
         } else if (icr.internalCacheHasFlag(cacheName, InternalCacheRegistry.Flag.PROTECTED)) {
            // We want to make sure the cache access is checked everytime, so don't store it as a "known" cache. More
            // expensive, but these caches should not be accessed frequently
            cache = server.getCacheInstance(cacheName, cacheManager, true, false);
         } else if (!cacheName.isEmpty() && !cacheManager.getCacheNames().contains(cacheName)) {
            throw new CacheNotFoundException(
                  format("Cache with name '%s' not found amongst the configured caches", cacheName),
                  header.version, header.messageId);
         } else {
            cache = server.getCacheInstance(cacheName, cacheManager, true, true);
         }
      }
      this.cache = decoder.getOptimizedCache(header, cache, server.getCacheConfiguration(cacheName));
   }

   void withSubect(Subject subject) {
      this.subject = subject;
      this.cache = cache.withSubject(subject);
   }

   public String getPrincipalName() {
      return subject != null ? Security.getSubjectUserPrincipal(subject).getName() : null;
   }

   Metadata buildMetadata() {
      EmbeddedMetadata.Builder metadata = new EmbeddedMetadata.Builder();
      metadata.version(generateVersion(server.getCacheRegistry(header.cacheName), cache));
      if (params.lifespan.duration != ServerConstants.EXPIRATION_DEFAULT) {
         metadata.lifespan(toMillis(params.lifespan, header));
      }
      if (params.maxIdle.duration != ServerConstants.EXPIRATION_DEFAULT) {
         metadata.maxIdle(toMillis(params.maxIdle, header));
      }
      return metadata.build();
   }

   Response get() {
      return createGetResponse(cache.getCacheEntry(key));
   }

   Response getKeyMetadata() {
      CacheEntry<byte[], byte[]> ce = cache.getCacheEntry(key);
      if (ce != null) {
         EntryVersion entryVersion = ce.getMetadata().version();
         long version = extractVersion(entryVersion);
         byte[] v = ce.getValue();
         int lifespan = ce.getLifespan() < 0 ? -1 : (int) ce.getLifespan() / 1000;
         int maxIdle = ce.getMaxIdle() < 0 ? -1 : (int) ce.getMaxIdle() / 1000;
         if (header.op == HotRodOperation.GET_WITH_METADATA) {
            return new GetWithMetadataResponse(header.version, header.messageId, header.cacheName, header.clientIntel,
                  header.op, OperationStatus.Success, header.topologyId, v, version,
                  ce.getCreated(), lifespan, ce.getLastUsed(), maxIdle);
         } else {
            int offset = (Integer) operationDecodeContext;
            return new GetStreamResponse(header.version, header.messageId, header.cacheName, header.clientIntel,
                  header.op, OperationStatus.Success, header.topologyId, v, offset, version,
                  ce.getCreated(), lifespan, ce.getLastUsed(), maxIdle);
         }
      } else {
         if (header.op == HotRodOperation.GET_WITH_METADATA) {
            return new GetWithMetadataResponse(header.version, header.messageId, header.cacheName, header.clientIntel,
                  header.op, OperationStatus.KeyDoesNotExist, header.topologyId);
         } else {
            return new GetStreamResponse(header.version, header.messageId, header.cacheName, header.clientIntel,
                  header.op, OperationStatus.KeyDoesNotExist, header.topologyId);
         }
      }
   }

   static long extractVersion(EntryVersion entryVersion) {
      long version = 0;
      if (entryVersion != null) {
         if (entryVersion instanceof NumericVersion) {
            version = NumericVersion.class.cast(entryVersion).getVersion();
         }
         if (entryVersion instanceof SimpleClusteredVersion) {
            version = SimpleClusteredVersion.class.cast(entryVersion).getVersion();
         }
      }
      return version;
   }

   Response containsKey() {
      if (cache.containsKey(key))
         return successResp(null);
      else
         return notExistResp();
   }

   Response replaceIfUnmodified() {
      CacheEntry<byte[], byte[]> entry = cache.withFlags(Flag.SKIP_LISTENER_NOTIFICATION).getCacheEntry(key);
      if (entry != null) {
         byte[] prev = entry.getValue();
         NumericVersion streamVersion = new NumericVersion(params.streamVersion);
         if (entry.getMetadata().version().equals(streamVersion)) {
            // Generate new version only if key present and version has not changed, otherwise it's wasteful
            boolean replaced = cache.replace(key, prev, (byte[]) operationDecodeContext, buildMetadata());
            if (replaced)
               return successResp(prev);
            else
               return notExecutedResp(prev);
         } else {
            return notExecutedResp(prev);
         }
      } else return notExistResp();
   }

   Response putIfAbsent() {
      byte[] prev = cache.get(key);
      if (prev == null) {
         // Generate new version only if key not present
         prev = cache.putIfAbsent(key, (byte[]) operationDecodeContext, buildMetadata());
      }
      if (prev == null)
         return successResp(null);
      else
         return notExecutedResp(prev);
   }

   Response put() {
      // Get an optimised cache in case we can make the operation more efficient
      byte[] prev = cache.put(key, (byte[]) operationDecodeContext, buildMetadata());
      return successResp(prev);
   }

   EntryVersion generateVersion(ComponentRegistry registry, Cache<byte[], byte[]> cache) {
      VersionGenerator cacheVersionGenerator = registry.getVersionGenerator();
      if (cacheVersionGenerator == null) {
         // It could be null, for example when not running in compatibility mode.
         // The reason for that is that if no other component depends on the
         // version generator, the factory does not get invoked.
         NumericVersionGenerator newVersionGenerator = new NumericVersionGenerator()
               .clustered(registry.getComponent(RpcManager.class) != null);
         registry.registerComponent(newVersionGenerator, VersionGenerator.class);
         return newVersionGenerator.generateNew();
      } else {
         return cacheVersionGenerator.generateNew();
      }
   }

   Response remove() {
      byte[] prev = cache.remove(key);
      if (prev != null)
         return successResp(prev);
      else
         return notExistResp();
   }

   Response removeIfUnmodified() {
      CacheEntry<byte[], byte[]> entry = cache.getCacheEntry(key);
      if (entry != null) {
         byte[] prev = entry.getValue();
         NumericVersion streamVersion = new NumericVersion(params.streamVersion);
         if (entry.getMetadata().version().equals(streamVersion)) {
            boolean removed = cache.remove(key, prev);
            if (removed)
               return successResp(prev);
            else
               return notExecutedResp(prev);
         } else {
            return notExecutedResp(prev);
         }
      } else {
         return notExistResp();
      }
   }

   Response clear() {
      cache.clear();
      return successResp(null);
   }

   Response successResp(byte[] prev) {
      return decoder.createSuccessResponse(header, prev);
   }

   Response notExecutedResp(byte[] prev) {
      return decoder.createNotExecutedResponse(header, prev);
   }

   Response notExistResp() {
      return decoder.createNotExistResponse(header);
   }

   Response createGetResponse(CacheEntry<byte[], byte[]> entry) {
      return decoder.createGetResponse(header, entry);
   }

   ComponentRegistry getCacheRegistry(String cacheName) {
      return server.getCacheRegistry(cacheName);
   }

   static class ExpirationParam {
      final long duration;
      final TimeUnitValue unit;

      ExpirationParam(long duration, TimeUnitValue unit) {
         this.duration = duration;
         this.unit = unit;
      }

      @Override
      public String toString() {
         final StringBuffer sb = new StringBuffer("ExpirationParam{");
         sb.append("duration=").append(duration);
         sb.append(", unit=").append(unit);
         sb.append('}');
         return sb.toString();
      }
   }

   static class RequestParameters {
      final int valueLength;
      final ExpirationParam lifespan;
      final ExpirationParam maxIdle;
      final long streamVersion;

      RequestParameters(int valueLength, ExpirationParam lifespan, ExpirationParam maxIdle, long streamVersion) {
         this.valueLength = valueLength;
         this.lifespan = lifespan;
         this.maxIdle = maxIdle;
         this.streamVersion = streamVersion;
      }

      @Override
      public String toString() {
         final StringBuffer sb = new StringBuffer("RequestParameters{");
         sb.append("valueLength=").append(valueLength);
         sb.append(", lifespan=").append(lifespan);
         sb.append(", maxIdle=").append(maxIdle);
         sb.append(", streamVersion=").append(streamVersion);
         sb.append('}');
         return sb.toString();
      }
   }

   private void applyCounter(String counterName,
         Consumer<Response> sendResponse,
         BiConsumer<StrongCounter, Consumer<Response>> applyStrong,
         BiConsumer<WeakCounter, Consumer<Response>> applyWeak) {
      CounterConfiguration config = counterManager.getConfiguration(counterName);
      if (config == null) {
         sendResponse.accept(missingCounterResponse());
         return;
      }
      switch (config.type()) {
         case UNBOUNDED_STRONG:
         case BOUNDED_STRONG:
            applyStrong.accept(counterManager.getStrongCounter(counterName), sendResponse);
            break;
         case WEAK:
            applyWeak.accept(counterManager.getWeakCounter(counterName), sendResponse);
            break;
      }
   }

   private Response createResponseFrom(ListenerOperationStatus status) {
      switch (status) {
         case OK:
            return createEmptyResponse(header, OperationStatus.OperationNotExecuted);
         case OK_AND_CHANNEL_IN_USE:
            return createEmptyResponse(header, OperationStatus.Success);
         case COUNTER_NOT_FOUND:
            return missingCounterResponse();
         default:
            throw new IllegalStateException();
      }
   }

   private BiConsumer<Void, Throwable> voidResultHandler(Consumer<Response> sendResponse) {
      return (value, throwable) -> {
         if (throwable != null) {
            checkCounterThrowable(sendResponse, throwable);
         } else {
            sendResponse.accept(createEmptyResponse(header, OperationStatus.Success));
         }
      };
   }

   /**
    * Transforms lifespan pass as seconds into milliseconds following this rule (inspired by Memcached):
    * <p>
    * If lifespan is bigger than number of seconds in 30 days, then it is considered unix time. After converting it to
    * milliseconds, we subtract the current time in and the result is returned.
    * <p>
    * Otherwise it's just considered number of seconds from now and it's returned in milliseconds unit.
    */
   static long toMillis(ExpirationParam param, HotRodHeader h) {
      if (param.duration > 0) {
         long milliseconds = param.unit.toTimeUnit().toMillis(param.duration);
         if (milliseconds > MillisecondsIn30days) {
            long unixTimeExpiry = milliseconds - System.currentTimeMillis();
            return unixTimeExpiry < 0 ? 0 : unixTimeExpiry;
         } else {
            return milliseconds;
         }
      } else {
         return param.duration;
      }
   }
}
