package org.infinispan.server.hotrod.iteration;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

import org.infinispan.commons.CacheException;
import org.infinispan.commons.configuration.ClassWhiteList;
import org.infinispan.commons.marshall.Marshaller;
import org.infinispan.commons.marshall.jboss.GenericJBossMarshaller;
import org.infinispan.filter.KeyValueFilterConverter;

/**
 * @author gustavonalle
 * @since 8.0
 */
class MarshallerBuilder {
   static <K, V, C> Class<?> toClass(IterationFilter<K, V, C> filter) {
      return filter.marshaller.map(Object::getClass).orElse(null);
   }

   private static Constructor<? extends Marshaller> findClassloaderConstructor(Class<? extends Marshaller> clazz) {
      try {
         return clazz.getConstructor(ClassLoader.class);
      } catch (NoSuchMethodException e) {
         return null;
      }
   }

   private static <T> Marshaller constructMarshaller(T t, Class<? extends Marshaller> marshallerClass) {
      Constructor<? extends Marshaller> constructor = findClassloaderConstructor(marshallerClass);
      try {
         if (constructor != null) {
            return constructor.newInstance(t.getClass().getClassLoader());
         } else {
            return marshallerClass.newInstance();
         }
      } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
         throw new CacheException(e);
      }
   }

   static <K, V, C> Marshaller fromClass(Class<? extends Marshaller> marshallerClazz,
                                         Optional<KeyValueFilterConverter<K, V, C>> filter, ClassWhiteList classWhiteList) {
      if (filter.isPresent()) {
         if (marshallerClazz != null) return constructMarshaller(filter.get(), marshallerClazz);
         return genericFromInstance(filter, classWhiteList);
      } else {
         return genericFromInstance(filter, classWhiteList);
      }
   }

   static Marshaller genericFromInstance(Optional<?> instance, ClassWhiteList classWhiteList) {
      return new GenericJBossMarshaller(instance.map(i -> i.getClass().getClassLoader()).orElse(null), classWhiteList);
   }
}
