package org.infinispan.tx.synchronisation;

import static org.testng.Assert.assertEquals;

import javax.transaction.NotSupportedException;
import javax.transaction.SystemException;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.StorageType;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.infinispan.transaction.lookup.DummyTransactionManagerLookup;
import org.infinispan.transaction.tm.DummyTransaction;
import org.infinispan.tx.LocalModeTxTest;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

/**
 * @author Mircea.Markus@jboss.com
 * @since 5.0
 */
@Test(groups = "functional", testName = "tx.synchronisation.LocalModeWithSyncTxTest")
public class LocalModeWithSyncTxTest extends LocalModeTxTest {

   @Override
   protected EmbeddedCacheManager createCacheManager() {
      ConfigurationBuilder config = getDefaultStandaloneCacheConfig(true);
      config.transaction().transactionManagerLookup(new DummyTransactionManagerLookup()).useSynchronization(true);
      return TestCacheManagerFactory.createCacheManager(config);
   }

   @Factory
   public Object[] factory() {
      return new Object[] {
            new LocalModeWithSyncTxTest().withStorage(StorageType.BINARY),
            new LocalModeWithSyncTxTest().withStorage(StorageType.OBJECT),
            new LocalModeWithSyncTxTest().withStorage(StorageType.OFF_HEAP)
      };
   }

   public void testSyncRegisteredWithCommit() throws Exception {
      DummyTransaction dt = startTx();
      tm().commit();
      assertEquals(0, dt.getEnlistedResources().size());
      assertEquals(0, dt.getEnlistedSynchronization().size());
      assertEquals("v", cache.get("k"));
   }

   public void testSyncRegisteredWithRollback() throws Exception {
      DummyTransaction dt = startTx();
      tm().rollback();
      assertEquals(null, cache.get("k"));
      assertEquals(0, dt.getEnlistedResources().size());
      assertEquals(0, dt.getEnlistedSynchronization().size());
   }

   private DummyTransaction startTx() throws NotSupportedException, SystemException {
      tm().begin();
      cache.put("k","v");
      DummyTransaction dt = (DummyTransaction) tm().getTransaction();
      assertEquals(0, dt.getEnlistedResources().size());
      assertEquals(1, dt.getEnlistedSynchronization().size());
      cache.put("k2","v2");
      assertEquals(0, dt.getEnlistedResources().size());
      assertEquals(1, dt.getEnlistedSynchronization().size());
      return dt;
   }
}
