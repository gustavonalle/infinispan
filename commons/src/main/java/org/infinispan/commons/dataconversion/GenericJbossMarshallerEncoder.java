package org.infinispan.commons.dataconversion;

import org.infinispan.commons.configuration.ClassWhiteList;
import org.infinispan.commons.marshall.jboss.GenericJBossMarshaller;

/**
 * @since 9.1
 */
public class GenericJbossMarshallerEncoder extends MarshallerEncoder {

   public GenericJbossMarshallerEncoder(ClassWhiteList classWhiteList, ClassLoader classLoader) {
      super(new GenericJBossMarshaller(classLoader, classWhiteList));
   }

   @Override
   public MediaType getStorageFormat() {
      return MediaType.APPLICATION_JBOSS_MARSHALLING;
   }

   @Override
   public short id() {
      return EncoderIds.GENERIC_MARSHALLER;
   }
}
