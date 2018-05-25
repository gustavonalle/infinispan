package org.infinispan.server.hotrod.iteration;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Optional;
import java.util.Set;

import org.infinispan.Cache;
import org.infinispan.commons.CacheException;
import org.infinispan.commons.configuration.ClassWhiteList;
import org.infinispan.commons.marshall.AbstractExternalizer;
import org.infinispan.commons.marshall.Marshaller;
import org.infinispan.commons.util.Util;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.filter.AbstractKeyValueFilterConverter;
import org.infinispan.filter.KeyValueFilterConverter;
import org.infinispan.metadata.Metadata;

/**
 * @author gustavonalle
 * @author wburns
 * @since 8.0
 */

public class IterationFilter<K, V, C> extends AbstractKeyValueFilterConverter<K, V, C> {
   final boolean compat;
   final Optional<KeyValueFilterConverter<K, V, C>> providedFilter;
   Optional<Marshaller> marshaller;
   final boolean binary;

   protected Marshaller filterMarshaller;
   Class<? extends Marshaller> marshallerClass;

   public IterationFilter(boolean compat, Optional<KeyValueFilterConverter<K, V, C>> providedFilter,
                          Optional<Marshaller> marshaller, boolean binary) {
      this.compat = compat;
      this.providedFilter = providedFilter;
      this.marshaller = marshaller;
      this.binary = binary;
   }

   private IterationFilter(boolean compat, Optional<KeyValueFilterConverter<K, V, C>> providedFilter,
                           Class<? extends Marshaller> marshaller, boolean binary) {
      this.compat = compat;
      this.providedFilter = providedFilter;
      this.marshallerClass = marshaller;
      this.binary = binary;
   }

   @Override
   public C filterAndConvert(K key, V value, Metadata metadata) {
      if (providedFilter.isPresent()) {
         KeyValueFilterConverter<K, V, C> f = providedFilter.get();
         if (!compat && !binary) {
            try {
               K unmarshalledKey = (K) filterMarshaller.objectFromByteBuffer((byte[]) key);
               V unmarshalledValue = (V) filterMarshaller.objectFromByteBuffer((byte[]) value);
               C result = f.filterAndConvert(unmarshalledKey, unmarshalledValue, metadata);
               if (result != null) {
                  return (C) filterMarshaller.objectToByteBuffer(result);
               } else {
                  return null;
               }
            } catch (IOException | ClassNotFoundException | InterruptedException e) {
               throw new CacheException(e);
            }
         } else {
            return f.filterAndConvert(key, value, metadata);
         }
      } else {
         return (C) value;
      }
   }

   @Inject
   public void injectDependencies(Cache cache) {
      ClassWhiteList classWhiteList = cache.getCacheManager().getClassWhiteList();
      if (marshaller == null) {
         marshaller = Optional.ofNullable(MarshallerBuilder.fromClass(marshallerClass, providedFilter, classWhiteList));
      }
      filterMarshaller = compat ? cache.getCacheConfiguration().compatibility().marshaller() :
            marshaller.orElse(MarshallerBuilder.genericFromInstance(providedFilter, classWhiteList));
      providedFilter.ifPresent(kvfc -> cache.getAdvancedCache().getComponentRegistry().wireDependencies(kvfc));

   }

   public static class IterationFilterExternalizer extends AbstractExternalizer<IterationFilter> {
      @Override
      public Set<Class<? extends IterationFilter>> getTypeClasses() {
         return Util.asSet(IterationFilter.class);
      }

      @Override
      public void writeObject(ObjectOutput output, IterationFilter object) throws IOException {
         output.writeBoolean(object.compat);
         output.writeBoolean(object.binary);
         if (object.providedFilter.isPresent()) {
            output.writeBoolean(true);
            output.writeObject(object.providedFilter.get());
         } else {
            output.writeBoolean(false);
         }
         Class<?> marshallerClass = MarshallerBuilder.toClass(object);
         if (marshallerClass != null) {
            output.writeBoolean(true);
            output.writeObject(marshallerClass);
         } else {
            output.writeBoolean(false);
         }
      }

      @Override
      public IterationFilter readObject(ObjectInput input) throws IOException, ClassNotFoundException {
         boolean compat = input.readBoolean();
         boolean binary = input.readBoolean();

         Optional<KeyValueFilterConverter> filter;
         if (input.readBoolean()) {
            filter = Optional.of((KeyValueFilterConverter) input.readObject());
         } else {
            filter = Optional.empty();
         }

         Class<Marshaller> marshallerClass = input.readBoolean() ? (Class) input.readObject() : null;

         return new IterationFilter(compat, filter, marshallerClass, binary);
      }
   }
}
