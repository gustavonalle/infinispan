package org.infinispan.server.hotrod.test

import java.util.function.Consumer

import org.infinispan.manager.EmbeddedCacheManager
import org.infinispan.server.core.test.Stoppable
import org.infinispan.server.hotrod.HotRodServer
import org.infinispan.server.hotrod.configuration.HotRodServerConfigurationBuilder
import org.infinispan.server.hotrod.test.HotRodTestingUtil._
import org.infinispan.test.fwk.TestCacheManagerFactory._
import org.testng.Assert._
import org.testng.annotations.Test

/**
  * Tests HotRod server start with SSL enabled and keystore which has different keystore and certificate passwords.
  *
  * @author vjuranek
  * @since 8.3
  */
@Test(groups = Array("functional"), testName = "server.hotrod.HotRodSslWithCertPasswdTest")
class HotRodSslWithCertPasswdTest {

   private val keyStoreFileName = getClass.getClassLoader.getResource("password_server_keystore.jks").getPath
   private val trustStoreFileName = getClass.getClassLoader.getResource("password_client_truststore.jks").getPath

   def testServerStartWithSslAndCertPasswd() {
      val builder = new HotRodServerConfigurationBuilder
      builder.host(host).port(UniquePortThreadLocal.get.intValue).idleTimeout(0)
      builder.ssl.enable().keyStoreFileName(keyStoreFileName).keyStorePassword("secret".toCharArray).keyStoreCertificatePassword("secret2".toCharArray).trustStoreFileName(trustStoreFileName).trustStorePassword("secret".toCharArray)
      Stoppable.useCacheManager(createCacheManager(hotRodCacheConfiguration()), new Consumer[EmbeddedCacheManager] {
         override def accept(cm: EmbeddedCacheManager): Unit = {
            Stoppable.useServer(new HotRodServer, new Consumer[HotRodServer] {
               override def accept(server: HotRodServer): Unit = {
                  server.start(builder.build, cm)
                  assertNotNull(server.getConfiguration.ssl().keyStoreCertificatePassword())
               }
            })
         }
      })
   }

}
