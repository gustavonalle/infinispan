package org.infinispan.server.hotrod.counter;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.stream.Collectors;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.counter.api.CounterManager;
import org.infinispan.counter.api.WeakCounter;
import org.infinispan.server.hotrod.Constants;
import org.infinispan.server.hotrod.counter.impl.TestCounterManager;
import org.infinispan.server.hotrod.counter.impl.WeakCounterImplTestStrategy;
import org.testng.annotations.Test;

/**
 * A {@link WeakCounter} api test.
 *
 * @author Pedro Ruivo
 * @since 8.5
 */
@Test(groups = "functional", testName = "server.hotrod.counter.WeakCounterAPITest")
public class WeakCounterAPITest extends HotRodMultiNodeTest implements WeakCounterTestStrategy {

   private final WeakCounterTestStrategy strategy;

   public WeakCounterAPITest() {
      strategy = new WeakCounterImplTestStrategy(this::testCounterManager, this::allTestCounterManager);
   }

   @Override
   public void testAdd(Method method) {
      strategy.testAdd(method);
   }

   @Override
   public void testReset(Method method) {
      strategy.testReset(method);
   }

   @Override
   public void testNameAndConfigurationTest(Method method) {
      strategy.testNameAndConfigurationTest(method);
   }

   @Override
   public void testRemove(Method method) {
      strategy.testRemove(method);
   }

   @Override
   public void testListenerAddAndRemove(Method method) throws InterruptedException {
      strategy.testListenerAddAndRemove(method);
   }

   @Override
   protected String cacheName() {
      return "unused";
   }

   @Override
   protected ConfigurationBuilder createCacheConfig() {
      return new ConfigurationBuilder();
   }

   @Override
   protected byte protocolVersion() {
      return Constants.VERSION_27;
   }

   private CounterManager testCounterManager() {
      return new TestCounterManager(clients().get(0));
   }

   private Collection<CounterManager> allTestCounterManager() {
      return clients().stream().map(TestCounterManager::new).collect(Collectors.toList());
   }

}
