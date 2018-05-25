package org.infinispan.rest;

import static org.infinispan.server.core.ExternalizerIds.MIME_METADATA;

import org.infinispan.commons.configuration.ClassWhiteList;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.factories.GlobalComponentRegistry;
import org.infinispan.lifecycle.AbstractModuleLifecycle;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.marshall.core.EncoderRegistry;
import org.infinispan.rest.dataconversion.JBossMarshallingTranscoder;
import org.infinispan.rest.dataconversion.JavaSerializationTranscoder;
import org.infinispan.rest.dataconversion.JsonTranscoder;
import org.infinispan.rest.dataconversion.XMLTranscoder;
import org.infinispan.rest.operations.mime.MimeMetadata;

/**
 * Module lifecycle callbacks implementation that enables module specific {@link org.infinispan.commons.marshall.AdvancedExternalizer}
 * implementations to be registered.
 *
 * @author Galder Zamarreño
 * @since 5.3
 */
public class LifecycleCallbacks extends AbstractModuleLifecycle {

   @Override
   public void cacheManagerStarting(GlobalComponentRegistry gcr, GlobalConfiguration globalConfiguration) {
      globalConfiguration.serialization().advancedExternalizers().put(
            MIME_METADATA, new MimeMetadata.Externalizer());
      EncoderRegistry encoderRegistry = gcr.getComponent(EncoderRegistry.class);
      ClassWhiteList classWhiteList = gcr.getComponent(EmbeddedCacheManager.class).getClassWhiteList();
      encoderRegistry.registerTranscoder(new XMLTranscoder(classWhiteList));
      encoderRegistry.registerTranscoder(new JsonTranscoder(classWhiteList));
      encoderRegistry.registerTranscoder(new JavaSerializationTranscoder(classWhiteList));
      encoderRegistry.registerTranscoder(new JBossMarshallingTranscoder(encoderRegistry));
   }
}
