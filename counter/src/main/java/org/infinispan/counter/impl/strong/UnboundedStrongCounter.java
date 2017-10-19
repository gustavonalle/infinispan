package org.infinispan.counter.impl.strong;

import org.infinispan.AdvancedCache;
import org.infinispan.counter.api.CounterConfiguration;
import org.infinispan.counter.impl.entries.CounterValue;
import org.infinispan.counter.impl.listener.CounterManagerNotificationManager;

/**
 * An unbounded strong consistent counter.
 *
 * @author Pedro Ruivo
 * @see AbstractStrongCounter
 * @since 8.5
 */
public class UnboundedStrongCounter extends AbstractStrongCounter {

   public UnboundedStrongCounter(String counterName, AdvancedCache<StrongCounterKey, CounterValue> cache,
         CounterConfiguration configuration, CounterManagerNotificationManager notificationManager) {
      super(counterName, cache, configuration, notificationManager);
   }

   @Override
   protected Boolean handleCASResult(Object state) {
      return (Boolean) state;
   }

   @Override
   protected long handleAddResult(CounterValue counterValue) {
      return counterValue.getValue();
   }

   @Override
   public String toString() {
      return "UnboundedStrongCounter{" +
            "counterName=" + key.getCounterName() +
            '}';
   }
}
