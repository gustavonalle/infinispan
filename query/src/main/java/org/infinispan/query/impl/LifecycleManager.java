package org.infinispan.query.impl;

import static org.infinispan.query.logging.Log.CONTAINER;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.management.ObjectName;

import org.apache.lucene.search.BooleanQuery;
import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurer;
import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.commons.CacheConfigurationException;
import org.infinispan.commons.CacheException;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.commons.marshall.AdvancedExternalizer;
import org.infinispan.commons.util.AggregatedClassLoader;
import org.infinispan.commons.util.ServiceFinder;
import org.infinispan.commons.util.TypedProperties;
import org.infinispan.commons.util.Util;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.IndexingConfiguration;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.factories.ComponentRegistry;
import org.infinispan.factories.GlobalComponentRegistry;
import org.infinispan.factories.annotations.InfinispanModule;
import org.infinispan.factories.impl.BasicComponentRegistry;
import org.infinispan.factories.impl.ComponentRef;
import org.infinispan.interceptors.AsyncInterceptor;
import org.infinispan.interceptors.AsyncInterceptorChain;
import org.infinispan.interceptors.impl.CacheLoaderInterceptor;
import org.infinispan.interceptors.impl.EntryWrappingInterceptor;
import org.infinispan.jmx.CacheJmxRegistration;
import org.infinispan.lifecycle.ModuleLifecycle;
import org.infinispan.marshall.protostream.impl.SerializationContextRegistry;
import org.infinispan.metrics.impl.CacheMetricsRegistration;
import org.infinispan.objectfilter.impl.syntax.parser.ReflectionEntityNamesResolver;
import org.infinispan.query.Indexer;
import org.infinispan.query.Transformer;
import org.infinispan.query.backend.KeyTransformationHandler;
import org.infinispan.query.backend.QueryInterceptor;
import org.infinispan.query.backend.TxQueryInterceptor;
import org.infinispan.query.clustered.ClusteredQueryOperation;
import org.infinispan.query.clustered.NodeTopDocs;
import org.infinispan.query.clustered.QueryResponse;
import org.infinispan.query.core.impl.QueryCache;
import org.infinispan.query.dsl.embedded.impl.ObjectReflectionMatcher;
import org.infinispan.query.dsl.embedded.impl.QueryEngine;
import org.infinispan.query.impl.externalizers.ExternalizerIds;
import org.infinispan.query.impl.externalizers.LuceneBytesRefExternalizer;
import org.infinispan.query.impl.externalizers.LuceneFieldDocExternalizer;
import org.infinispan.query.impl.externalizers.LuceneScoreDocExternalizer;
import org.infinispan.query.impl.externalizers.LuceneSortExternalizer;
import org.infinispan.query.impl.externalizers.LuceneSortFieldExternalizer;
import org.infinispan.query.impl.externalizers.LuceneTopDocsExternalizer;
import org.infinispan.query.impl.externalizers.LuceneTopFieldDocsExternalizer;
import org.infinispan.query.impl.externalizers.PojoRawTypeIdentifierExternalizer;
import org.infinispan.query.impl.externalizers.LuceneTotalHitsExternalizer;
import org.infinispan.query.impl.massindex.DistributedExecutorMassIndexer;
import org.infinispan.query.impl.massindex.IndexWorker;
import org.infinispan.query.logging.Log;
import org.infinispan.registry.InternalCacheRegistry;
import org.infinispan.registry.InternalCacheRegistry.Flag;
import org.infinispan.search.mapper.mapping.SearchMapping;
import org.infinispan.search.mapper.mapping.SearchMappingBuilder;
import org.infinispan.search.mapper.mapping.ProgrammaticSearchMappingProvider;
import org.infinispan.search.mapper.mapping.SearchMappingHolder;
import org.infinispan.search.mapper.mapping.impl.CompositeAnalysisConfigurer;
import org.infinispan.transaction.xa.GlobalTransaction;
import org.infinispan.util.logging.LogFactory;

/**
 * Lifecycle of the Query module: initializes the Hibernate Search engine and shuts it down at cache stop. Each cache
 * manager has its own instance of this class during its lifetime.
 *
 * @author Sanne Grinovero &lt;sanne@hibernate.org&gt; (C) 2011 Red Hat Inc.
 */
@InfinispanModule(name = "query", requiredModules = {"core", "query-core", "clustered-lock"}, optionalModules = "lucene-directory")
public class LifecycleManager implements ModuleLifecycle {

   private static final Log log = LogFactory.getLog(LifecycleManager.class, Log.class);

   private static final String ANALYSIS_CONFIGURER_PROPERTY_NAME = "analysis.configurer";
   private static final String HS5_CONF_STRATEGY_PROPERTY = "hibernate.search.indexing_strategy";
   private static final String HS5_CONF_STRATEGY_MANUAL = "manual";

   /**
    * Optional integer system property that sets value of {@link BooleanQuery#setMaxClauseCount}.
    */
   public static final String MAX_BOOLEAN_CLAUSES_SYS_PROP = "infinispan.query.lucene.max-boolean-clauses";

   private static boolean maxBooleanClausesWasSet = false;

   /**
    * Registers the Search interceptor in the cache before it gets started
    */
   @Override
   public void cacheStarting(ComponentRegistry cr, Configuration cfg, String cacheName) {
      InternalCacheRegistry icr = cr.getGlobalComponentRegistry().getComponent(InternalCacheRegistry.class);
      if (!icr.isInternalCache(cacheName) || icr.internalCacheHasFlag(cacheName, Flag.QUERYABLE)) {
         AdvancedCache<?, ?> cache = cr.getComponent(Cache.class).getAdvancedCache();
         SecurityActions.addCacheDependency(cache.getCacheManager(), cacheName, QueryCache.QUERY_CACHE_NAME);

         ClassLoader aggregatedClassLoader = makeAggregatedClassLoader(cr.getGlobalComponentRegistry().getGlobalConfiguration().classLoader());
         boolean isIndexed = cfg.indexing().enabled();

         SearchMappingHolder searchMapping = null;
         if (isIndexed) {
            setBooleanQueryMaxClauseCount(cfg.indexing().properties());

            Map<String, Class<?>> indexedClasses = makeIndexedClassesMap(cache);

            KeyTransformationHandler keyTransformationHandler = new KeyTransformationHandler(aggregatedClassLoader);
            cr.registerComponent(keyTransformationHandler, KeyTransformationHandler.class);

            searchMapping = createSearchMapping(cfg.indexing(), indexedClasses, cr, cache, keyTransformationHandler,
                  aggregatedClassLoader);

            createQueryInterceptorIfNeeded(cr, cfg, cache, indexedClasses, searchMapping, keyTransformationHandler);

            DistributedExecutorMassIndexer massIndexer = new DistributedExecutorMassIndexer(cache, searchMapping,
                  keyTransformationHandler);
            cr.registerComponent(massIndexer, Indexer.class);
         }

         cr.registerComponent(ObjectReflectionMatcher.create(
               new ReflectionEntityNamesResolver(aggregatedClassLoader), searchMapping),
               ObjectReflectionMatcher.class);
         cr.registerComponent(new QueryEngine<>(cache, isIndexed), QueryEngine.class);
      }
   }

   private Map<String, Class<?>> makeIndexedClassesMap(AdvancedCache<?, ?> cache) {
      Configuration cacheConfiguration = cache.getCacheConfiguration();

      Map<String, Class<?>> entities = new HashMap<>();
      for (Class<?> c : cacheConfiguration.indexing().indexedEntities()) {
         // include classes declared in indexing config
         entities.put(c.getName(), c);
      }

      if (!cache.getValueDataConversion().getStorageMediaType().match(MediaType.APPLICATION_PROTOSTREAM)) {
         // Try to resolve the indexed type names to class names.
         for (String typeName : cacheConfiguration.indexing().indexedEntityTypes()) {
            if (!entities.containsKey(typeName)) {
               try {
                  Class<?> c = Util.loadClass(typeName, cache.getClassLoader());
                  entities.put(c.getName(), c);
               } catch (Exception e) {
                  throw new CacheConfigurationException("Failed to load declared indexed class", e);
               }
            }
         }
      }
      return entities;
   }

   private void createQueryInterceptorIfNeeded(ComponentRegistry cr, Configuration cfg, AdvancedCache<?, ?> cache, Map<String, Class<?>> indexedClasses,
                                               SearchMappingHolder searchMapping, KeyTransformationHandler keyTransformationHandler) {
      CONTAINER.registeringQueryInterceptor(cache.getName());

      BasicComponentRegistry bcr = cr.getComponent(BasicComponentRegistry.class);
      ComponentRef<QueryInterceptor> queryInterceptorRef = bcr.getComponent(QueryInterceptor.class);
      if (queryInterceptorRef != null) {
         // could be already present when two caches share a config
         return;
      }

      ConcurrentMap<GlobalTransaction, Map<Object, Object>> txOldValues = new ConcurrentHashMap<>();
      boolean manualIndexing = HS5_CONF_STRATEGY_MANUAL.equals(
            cfg.indexing().properties().get(HS5_CONF_STRATEGY_PROPERTY));

      QueryInterceptor queryInterceptor = new QueryInterceptor(searchMapping, keyTransformationHandler, manualIndexing,
            txOldValues, cache, indexedClasses);

      for (Map.Entry<Class<?>, Class<?>> kt : cfg.indexing().keyTransformers().entrySet()) {
         keyTransformationHandler.registerTransformer(kt.getKey(), (Class<? extends Transformer>) kt.getValue());
      }

      AsyncInterceptorChain ic = bcr.getComponent(AsyncInterceptorChain.class).wired();

      EntryWrappingInterceptor wrappingInterceptor = ic.findInterceptorExtending(EntryWrappingInterceptor.class);
      AsyncInterceptor lastLoadingInterceptor = ic.findInterceptorExtending(CacheLoaderInterceptor.class);
      if (lastLoadingInterceptor == null) {
         lastLoadingInterceptor = wrappingInterceptor;
      }

      ic.addInterceptorAfter(queryInterceptor, lastLoadingInterceptor.getClass());
      bcr.registerComponent(QueryInterceptor.class, queryInterceptor, true);
      bcr.addDynamicDependency(AsyncInterceptorChain.class.getName(), QueryInterceptor.class.getName());

      if (cfg.transaction().transactionMode().isTransactional()) {
         TxQueryInterceptor txQueryInterceptor = new TxQueryInterceptor(txOldValues, queryInterceptor);
         ic.addInterceptorBefore(txQueryInterceptor, wrappingInterceptor.getClass());
         bcr.registerComponent(TxQueryInterceptor.class, txQueryInterceptor, true);
         bcr.addDynamicDependency(AsyncInterceptorChain.class.getName(), TxQueryInterceptor.class.getName());
      }
   }

   @Override
   public void cacheStarted(ComponentRegistry cr, String cacheName) {
      Configuration configuration = cr.getComponent(Configuration.class);
      IndexingConfiguration indexingConfiguration = configuration.indexing();
      if (!indexingConfiguration.enabled()) {
         if (verifyChainContainsQueryInterceptor(cr)) {
            throw new IllegalStateException("It was NOT expected to find the Query interceptor registered in the InterceptorChain as indexing was disabled, but it was found");
         }
         return;
      }
      if (!verifyChainContainsQueryInterceptor(cr)) {
         throw new IllegalStateException("It was expected to find the Query interceptor registered in the InterceptorChain but it wasn't found");
      }

      SearchMappingHolder searchMapping = cr.getComponent(SearchMappingHolder.class);
      SearchMapping mapping = searchMapping.getSearchMapping();
      if (mapping != null) {
         checkIndexableClasses(mapping, indexingConfiguration.indexedEntities());
      }

      AdvancedCache<?, ?> cache = cr.getComponent(Cache.class).getAdvancedCache();
      Indexer massIndexer = ComponentRegistryUtils.getIndexer(cache);
      InfinispanQueryStatisticsInfo stats = new InfinispanQueryStatisticsInfo(searchMapping, massIndexer);
      stats.setStatisticsEnabled(configuration.statistics().enabled());
      cr.registerComponent(stats, InfinispanQueryStatisticsInfo.class);

      registerQueryMBeans(cr, massIndexer, stats);

      registerMetrics(cr, stats);
   }

   private void registerMetrics(ComponentRegistry cr, InfinispanQueryStatisticsInfo stats) {
      CacheMetricsRegistration cacheMetricsRegistration = cr.getComponent(CacheMetricsRegistration.class);
      if (cacheMetricsRegistration.metricsEnabled()) {
         cacheMetricsRegistration.registerMetrics(stats, "query", "statistics");
      }
   }

   /**
    * Check that the indexable classes declared by the user are really indexable by looking at the presence of Hibernate
    * Search index bindings.
    */
   private void checkIndexableClasses(SearchMapping searchMapping, Set<Class<?>> indexedEntities) {
      if (indexedEntities.isEmpty()) {
         return;
      }

      Collection<Class<?>> indexedTypes = searchMapping.allIndexedTypes().values();
      for (Class<?> c : indexedEntities) {
         if (!indexedTypes.contains(c)) {
            throw CONTAINER.classNotIndexable(c.getName());
         }
      }
   }

   /**
    * Register query statistics and mass-indexer MBeans for a cache.
    */
   private void registerQueryMBeans(ComponentRegistry cr, Indexer massIndexer, InfinispanQueryStatisticsInfo stats) {
      GlobalConfiguration globalConfig = cr.getGlobalComponentRegistry().getGlobalConfiguration();
      if (globalConfig.jmx().enabled()) {
         Cache<?, ?> cache = cr.getComponent(Cache.class);
         String queryGroupName = getQueryGroupName(globalConfig.cacheManagerName(), cache.getName());
         CacheJmxRegistration jmxRegistration = cr.getComponent(CacheJmxRegistration.class);
         try {
            jmxRegistration.registerMBean(stats, queryGroupName);
         } catch (Exception e) {
            throw new CacheException("Unable to register query statistics MBean", e);
         }
         try {
            jmxRegistration.registerMBean(massIndexer, queryGroupName);
         } catch (Exception e) {
            throw new CacheException("Unable to register MassIndexer MBean", e);
         }
      }
   }

   private String getQueryGroupName(String cacheManagerName, String cacheName) {
      return "type=Query,manager=" + ObjectName.quote(cacheManagerName) + ",cache=" + ObjectName.quote(cacheName);
   }

   private boolean verifyChainContainsQueryInterceptor(ComponentRegistry cr) {
      AsyncInterceptorChain interceptorChain = cr.getComponent(AsyncInterceptorChain.class);
      return interceptorChain != null && interceptorChain.containsInterceptorType(QueryInterceptor.class, true);
   }

   private SearchMappingHolder createSearchMapping(IndexingConfiguration indexingConfiguration,
                                                   Map<String, Class<?>> indexedClasses, ComponentRegistry cr,
                                                   AdvancedCache<?, ?> cache,
                                                   KeyTransformationHandler keyTransformationHandler,
                                                   ClassLoader aggregatedClassLoader) {
      SearchMappingHolder searchMappingHolder = cr.getComponent(SearchMappingHolder.class);
      if (searchMappingHolder != null && searchMappingHolder.getSearchMapping() != null && !searchMappingHolder.getSearchMapping().isClose()) {
         // a paranoid check against an unlikely failure
         throw new IllegalStateException("SearchIntegrator already initialized!");
      }

      // load ProgrammaticSearchMappingProviders from classpath
      Collection<ProgrammaticSearchMappingProvider> mappingProviders =
            ServiceFinder.load(ProgrammaticSearchMappingProvider.class, aggregatedClassLoader);

      Map<String, Object> properties = new LinkedHashMap<>();

      // load LuceneAnalysisDefinitionProvider from classpath
      Collection<LuceneAnalysisConfigurer> analyzerDefProviders = ServiceFinder.load(LuceneAnalysisConfigurer.class, aggregatedClassLoader);
      if (analyzerDefProviders.size() == 1) {
         properties.put(ANALYSIS_CONFIGURER_PROPERTY_NAME, analyzerDefProviders.iterator().next());
      } else if (!analyzerDefProviders.isEmpty()) {
         properties.put(ANALYSIS_CONFIGURER_PROPERTY_NAME, new CompositeAnalysisConfigurer(analyzerDefProviders));
      }

      // provide user defined properties
      for (Map.Entry<Object, Object> entry : indexingConfiguration.properties().entrySet()) {
         if (entry.getKey() instanceof String) {
            if (!(entry.getKey() instanceof String)) {
               throw log.invalidPropertyKey(entry.getKey());
            }
            properties.put((String) entry.getKey(), entry.getValue());
         }
      }

      searchMappingHolder = new SearchMappingHolder(CacheIdentifierBridge.getReference(), properties,
            aggregatedClassLoader, mappingProviders);
      Set<Class<?>> types = new HashSet<>( indexedClasses.values() );

      // TODO: look for protobuf entity type marked as indexed.
      if (!types.isEmpty()) {
         searchMappingHolder.setEntityLoader(new EntityLoader(cache, keyTransformationHandler));
         SearchMappingBuilder builder = searchMappingHolder.builder(SearchMappingBuilder.introspector(MethodHandles.lookup()));
         builder.addEntityTypes(types);
         searchMappingHolder.build();
      }

      cr.registerComponent(searchMappingHolder, SearchMappingHolder.class);
      return searchMappingHolder;
   }

   /**
    * Create a class loader that delegates loading to an ordered set of class loaders.
    *
    * @param globalClassLoader the cache manager's global ClassLoader from GlobalConfiguration
    * @return the aggregated ClassLoader
    */
   private ClassLoader makeAggregatedClassLoader(ClassLoader globalClassLoader) {
      // use an ordered set to deduplicate them
      Set<ClassLoader> classLoaders = new LinkedHashSet<>(6);

      // add the cache manager's CL
      if (globalClassLoader != null) {
         classLoaders.add(globalClassLoader);
      }

      // add Infinispan's CL
      classLoaders.add(AggregatedClassLoader.class.getClassLoader());

      // add this module's CL
      classLoaders.add(getClass().getClassLoader());

      // add the TCCL
      try {
         ClassLoader tccl = Thread.currentThread().getContextClassLoader();
         if (tccl != null) {
            classLoaders.add(tccl);
         }
      } catch (Exception e) {
         // ignored
      }

      // add the system CL
      try {
         ClassLoader syscl = ClassLoader.getSystemClassLoader();
         if (syscl != null) {
            classLoaders.add(syscl);
         }

      } catch (Exception e) {
         // ignored
      }

      return new AggregatedClassLoader(classLoaders);
   }

   @Override
   public void cacheStopping(ComponentRegistry cr, String cacheName) {
      QueryInterceptor queryInterceptor = cr.getComponent(QueryInterceptor.class);
      if (queryInterceptor != null) {
         queryInterceptor.prepareForStopping();
      }

      SearchMappingHolder searchMappingHolder = cr.getComponent(SearchMappingHolder.class);
      if (searchMappingHolder != null && searchMappingHolder.getSearchMapping() != null) {
         searchMappingHolder.getSearchMapping().close();
      }
   }

   @Override
   public void cacheStopped(ComponentRegistry cr, String cacheName) {
      QueryCache queryCache = cr.getComponent(QueryCache.class);
      if (queryCache != null) {
         InternalCacheRegistry icr = cr.getGlobalComponentRegistry().getComponent(InternalCacheRegistry.class);
         if (!icr.isInternalCache(cacheName) || icr.internalCacheHasFlag(cacheName, Flag.QUERYABLE)) {
            queryCache.clear(cacheName);
         }
      }
   }

   @Override
   public void cacheManagerStarting(GlobalComponentRegistry gcr, GlobalConfiguration globalCfg) {
      SerializationContextRegistry ctxRegistry = gcr.getComponent(SerializationContextRegistry.class);
      ctxRegistry.addContextInitializer(SerializationContextRegistry.MarshallerType.PERSISTENCE, new PersistenceContextInitializerImpl());

      Map<Integer, AdvancedExternalizer<?>> externalizerMap = globalCfg.serialization().advancedExternalizers();
      externalizerMap.put(ExternalizerIds.LUCENE_SORT, new LuceneSortExternalizer());
      externalizerMap.put(ExternalizerIds.LUCENE_SORT_FIELD, new LuceneSortFieldExternalizer());
      externalizerMap.put(ExternalizerIds.CLUSTERED_QUERY_TOPDOCS, new NodeTopDocs.Externalizer());
      externalizerMap.put(ExternalizerIds.LUCENE_TOPDOCS, new LuceneTopDocsExternalizer());
      externalizerMap.put(ExternalizerIds.LUCENE_FIELD_SCORE_DOC, new LuceneFieldDocExternalizer());
      externalizerMap.put(ExternalizerIds.LUCENE_SCORE_DOC, new LuceneScoreDocExternalizer());
      externalizerMap.put(ExternalizerIds.LUCENE_TOPFIELDDOCS, new LuceneTopFieldDocsExternalizer());
      externalizerMap.put(ExternalizerIds.INDEX_WORKER, new IndexWorker.Externalizer());
      externalizerMap.put(ExternalizerIds.LUCENE_BYTES_REF, new LuceneBytesRefExternalizer());
      externalizerMap.put(ExternalizerIds.QUERY_DEFINITION, new QueryDefinition.Externalizer());
      externalizerMap.put(ExternalizerIds.CLUSTERED_QUERY_COMMAND_RESPONSE, new QueryResponse.Externalizer());
      externalizerMap.put(ExternalizerIds.CLUSTERED_QUERY_OPERATION, new ClusteredQueryOperation.Externalizer());
      externalizerMap.put(ExternalizerIds.POJO_TYPE_IDENTIFIER, new PojoRawTypeIdentifierExternalizer());
      externalizerMap.put(ExternalizerIds.LUCENE_TOTAL_HITS, new LuceneTotalHitsExternalizer());
   }

   /**
    * Sets {@link BooleanQuery#setMaxClauseCount} according to the value of {@link #MAX_BOOLEAN_CLAUSES_SYS_PROP} system
    * property. This is executed only once, when first indexed cache is started.
    *
    * @param properties
    */
   private void setBooleanQueryMaxClauseCount(TypedProperties properties) {
      if (!maxBooleanClausesWasSet) {
         maxBooleanClausesWasSet = true;
         String maxClauseCountProp = properties.getProperty(MAX_BOOLEAN_CLAUSES_SYS_PROP);
         if (maxClauseCountProp == null) {
            maxClauseCountProp = SecurityActions.getSystemProperty(MAX_BOOLEAN_CLAUSES_SYS_PROP);
         }
         if (maxClauseCountProp != null) {
            int maxClauseCount;
            try {
               maxClauseCount = Integer.parseInt(maxClauseCountProp);
            } catch (NumberFormatException e) {
               CONTAINER.failedToParseSystemProperty(MAX_BOOLEAN_CLAUSES_SYS_PROP, e);
               throw e;
            }
            int currentMaxClauseCount = BooleanQuery.getMaxClauseCount();
            if (maxClauseCount > currentMaxClauseCount) {
               CONTAINER.settingBooleanQueryMaxClauseCount(MAX_BOOLEAN_CLAUSES_SYS_PROP, maxClauseCount);
               BooleanQuery.setMaxClauseCount(maxClauseCount);
            } else {
               CONTAINER.ignoringBooleanQueryMaxClauseCount(MAX_BOOLEAN_CLAUSES_SYS_PROP, maxClauseCount, currentMaxClauseCount);
            }
         }
      }
   }
}
