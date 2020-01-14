package org.infinispan.query.clustered.commandworkers;

import java.util.BitSet;

import org.apache.lucene.search.TopDocs;
import org.hibernate.search.query.engine.spi.DocumentExtractor;
import org.hibernate.search.query.engine.spi.HSQuery;
import org.infinispan.configuration.cache.HashConfiguration;
import org.infinispan.query.clustered.NodeTopDocs;
import org.infinispan.query.clustered.QueryResponse;

/**
 * Creates a DocumentExtractor and register it on the node QueryBox.
 *
 * @author Israel Lacerra &lt;israeldl@gmail.com&gt;
 * @since 5.1
 */
final class CQCreateLazyQuery extends CQWorker {

   @Override
   QueryResponse perform(BitSet segments) {
      HSQuery query = queryDefinition.getHsQuery();
      query.afterDeserialise(getSearchFactory());
      if (segments.cardinality() != HashConfiguration.NUM_SEGMENTS.getDefaultValue()) {
         query.enableFullTextFilter("segmentFilter").setParameter("segments", segments);
      }
      DocumentExtractor extractor = query.queryDocumentExtractor();

      // registering...
      getQueryBox().put(queryId, extractor);

      // returning the QueryResponse
      TopDocs topDocs = extractor.getTopDocs();
      return new QueryResponse(new NodeTopDocs(cache.getRpcManager().getAddress(), topDocs));
   }
}
