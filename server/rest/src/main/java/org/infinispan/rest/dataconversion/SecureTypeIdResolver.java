package org.infinispan.rest.dataconversion;

import org.codehaus.jackson.annotate.JsonTypeInfo;
import org.codehaus.jackson.map.jsontype.TypeIdResolver;
import org.codehaus.jackson.type.JavaType;
import org.infinispan.commons.configuration.ClassWhiteList;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * Jackson TypeIdResolver that checks the serialization whitelist before deserializing JSON types.
 */
public class SecureTypeIdResolver implements TypeIdResolver {

   protected final static Log logger = LogFactory.getLog(SecureTypeIdResolver.class, Log.class);

   private TypeIdResolver internalTypeIdResolver;
   private final ClassWhiteList whiteList;

   SecureTypeIdResolver(TypeIdResolver typeIdResolver, ClassWhiteList whiteList) {
      this.internalTypeIdResolver = typeIdResolver;
      this.whiteList = whiteList;
   }

   @Override
   public void init(JavaType baseType) {
      internalTypeIdResolver.init(baseType);
   }

   @Override
   public String idFromValue(Object value) {
      return internalTypeIdResolver.idFromValue(value);
   }

   @Override
   public String idFromValueAndType(Object value, Class<?> suggestedType) {
      return internalTypeIdResolver.idFromValueAndType(value, suggestedType);
   }

   @Override
   public JavaType typeFromId(String id) {
      JavaType javaType = internalTypeIdResolver.typeFromId(id);
      Class<?> rawClass = javaType.getRawClass();
      String className = rawClass.getName();
      if (!whiteList.isSafeClass(className)) {
         throw logger.errorDeserializing(className);
      }
      return javaType;
   }

   @Override
   public JsonTypeInfo.Id getMechanism() {
      return internalTypeIdResolver.getMechanism();
   }
}
