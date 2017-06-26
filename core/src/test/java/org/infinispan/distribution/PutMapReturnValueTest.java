package org.infinispan.distribution;

import static org.testng.Assert.assertNotNull;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.infinispan.AdvancedCache;
import org.infinispan.commands.CommandsFactory;
import org.infinispan.commands.write.PutMapCommand;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.context.InvocationContext;
import org.infinispan.context.InvocationContextFactory;
import org.infinispan.factories.ComponentRegistry;
import org.infinispan.test.MultipleCacheManagersTest;
import org.infinispan.test.TestingUtil;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

/**
 * Test a PutMapCommand invocation without the IGNORE_RETURN_VALUES flag.
 *
 * Only upstream includes AdvancedCache.getAndPutAll(), but interceptors can still remove the IGNORE_RETURN_VALUE flag.
 */
@Test(groups = "functional", testName = "distribution.PutMapReturnValueTest")
public class PutMapReturnValueTest extends MultipleCacheManagersTest {

   private AdvancedCache<Object, String> c1;
   private AdvancedCache<Object, String> c2;
   private CacheMode cacheMode;

   public PutMapReturnValueTest(CacheMode cacheMode) {
      this.cacheMode = cacheMode;
   }

   @Factory
   public static Object[] factory() {
      return new Object[]{
            new PutMapReturnValueTest(CacheMode.DIST_SYNC),
            new PutMapReturnValueTest(CacheMode.REPL_SYNC),
      };
   }

   @Override
   protected void createCacheManagers() throws Throwable {
      createCluster(getDefaultClusteredCacheConfig(cacheMode), 2);
      c1 = this.<Object, String>cache(0).getAdvancedCache();
      c2 = this.<Object, String>cache(1).getAdvancedCache();
   }

   public void testGetAndPutAll() {
      MagicKey k1 = new MagicKey(c1);
      MagicKey k2 = new MagicKey(c1);
      MagicKey k3 = new MagicKey(c2);
      MagicKey k4 = new MagicKey(c2);

      c1.put(k1, "v1-0");
      c2.put(k3, "v3-0");

      Map<Object, String> map = new HashMap<>();
      map.put(k1, "v1-1");
      map.put(k2, "v2-1");
      map.put(k3, "v3-1");
      map.put(k4, "v4-1");

      Map<Object, String> result = getAndPutAll(c1, map);
      assertNotNull(result);
      assertEquals(4, result.size());
      assertEquals("v1-0", result.get(k1));
      assertEquals("v3-0", result.get(k3));
      assertNull(result.get(k2));
      assertNull(result.get(k4));

      map.put(k1, "v1-2");
      map.put(k2, "v2-2");
      map.put(k3, "v3-2");
      map.put(k4, "v4-2");
      result = getAndPutAll(c1, map);
      assertNotNull(result);
      assertEquals(4, result.size());
      assertEquals("v1-1", result.get(k1));
      assertEquals("v2-1", result.get(k2));
      assertEquals("v3-1", result.get(k3));
      assertEquals("v4-1", result.get(k4));

      result = c1.getAll(map.keySet());
      assertEquals(4, result.size());
      assertEquals("v1-2", result.get(k1));
      assertEquals("v2-2", result.get(k2));
      assertEquals("v3-2", result.get(k3));
      assertEquals("v4-2", result.get(k4));
   }

   private Map<Object, String> getAndPutAll(AdvancedCache<Object, String> cache, Map<Object, String> map) {
      ComponentRegistry cr = cache.getComponentRegistry();
      InvocationContext ctx = cr.getComponent(InvocationContextFactory.class).createInvocationContext(true, map.size());
      PutMapCommand putMapCommand = cr.getComponent(CommandsFactory.class).buildPutMapCommand(map, null, 0);
      ctx.setLockOwner(putMapCommand.getKeyLockOwner());
      return (Map<Object, String>) c1.getAsyncInterceptorChain().invoke(ctx, putMapCommand);
   }
}
