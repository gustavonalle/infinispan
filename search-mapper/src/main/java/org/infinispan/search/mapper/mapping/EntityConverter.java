package org.infinispan.search.mapper.mapping;

/**
 * Can convert an entity before indexing it.
 *
 * @author Fabio Massimo Ercoli
 */
public interface EntityConverter {

   /**
    * @return The type that is supposed to be converted
    */
   Class<?> targetType();

   /**
    * @return The resulting converted type
    */
   Class<?> convertedType();

   /**
    * Perform the conversion.
    *
    * @param entity The entity to convert
    * @return The converted entity
    */
   ConvertedEntity convert(Object entity);

   /**
    * The result of an entity conversion
    */
   interface ConvertedEntity {

      boolean skip();

      String entityName();

      Object value();
   }

}
