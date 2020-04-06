package org.infinispan.marshaller.kryo;

import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.it.endpoints.EndpointsCacheFactory;
import org.infinispan.marshaller.test.AbstractInteropTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @author Ryan Emerson
 * @since 9.0
 */
@Test(groups = "functional", testName = "marshaller.kryo.KryoInteropTest")
public class KryoInteropTest extends AbstractInteropTest {

   @BeforeClass
   protected void setup() throws Exception {
      KryoMarshaller marshaller = new KryoMarshaller();
      cacheFactory = new EndpointsCacheFactory<>("KryoCache", marshaller, CacheMode.LOCAL);
      cacheFactory.setup();
   }
}
