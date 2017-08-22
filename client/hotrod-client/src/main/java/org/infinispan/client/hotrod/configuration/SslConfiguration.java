package org.infinispan.client.hotrod.configuration;

import java.util.Arrays;

import javax.net.ssl.SSLContext;

/**
 * SslConfiguration.
 *
 * @author Tristan Tarrant
 * @since 5.3
 */
public class SslConfiguration {
   private final boolean enabled;
   private final String keyStoreFileName;
   private final char[] keyStorePassword;
   private final char[] keyStoreCertificatePassword;
   private final String keyAlias;
   private final SSLContext sslContext;
   private final String trustStoreFileName;
   private final char[] trustStorePassword;
   private String sniHostName;

   SslConfiguration(boolean enabled, String keyStoreFileName, char[] keyStorePassword, char[] keyStoreCertificatePassword, String keyAlias,
                    SSLContext sslContext,
                    String trustStoreFileName, char[] trustStorePassword, String sniHostName) {
      this.enabled = enabled;
      this.keyStoreFileName = keyStoreFileName;
      this.keyStorePassword = keyStorePassword;
      this.keyStoreCertificatePassword = keyStoreCertificatePassword;
      this.keyAlias = keyAlias;
      this.sslContext = sslContext;
      this.trustStoreFileName = trustStoreFileName;
      this.trustStorePassword = trustStorePassword;
      this.sniHostName = sniHostName;
   }

   public boolean enabled() {
      return enabled;
   }

   public String keyStoreFileName() {
      return keyStoreFileName;
   }

   public char[] keyStorePassword() {
      return keyStorePassword;
   }

   public char[] keyStoreCertificatePassword() {
      return keyStoreCertificatePassword;
   }

   public String keyAlias() {
      return keyAlias;
   }

   public SSLContext sslContext() {
      return sslContext;
   }

   public String trustStoreFileName() {
      return trustStoreFileName;
   }

   public char[] trustStorePassword() {
      return trustStorePassword;
   }

   @Override
   public String toString() {
      return "SslConfiguration [" +
              "keyStoreFileName='" + keyStoreFileName + '\'' +
              ", enabled=" + enabled +
              ", keyStoreCertificatePassword=" + Arrays.toString(keyStoreCertificatePassword) +
              ", keyAlias='" + keyAlias + '\'' +
              ", sslContext=" + sslContext +
              ", trustStoreFileName='" + trustStoreFileName + '\'' +
              ", sniHostName=" + sniHostName +
              ']';
   }

   public String sniHostName() {
      return sniHostName;
   }
}
