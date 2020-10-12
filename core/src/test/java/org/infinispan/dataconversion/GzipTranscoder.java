package org.infinispan.dataconversion;

import static org.infinispan.commons.dataconversion.MediaType.APPLICATION_OBJECT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.infinispan.commons.dataconversion.EncodingException;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.commons.dataconversion.OneToManyTranscoder;

/**
 * Sample transcoder that will compress and uncompress any content using gzip.
 *
 * @since 12.0
 */
public class GzipTranscoder extends OneToManyTranscoder {
   public static final String APPLICATION_GZIP_TYPE = "application/gzip";
   public static final MediaType APPLICATION_GZIP = MediaType.fromString(APPLICATION_GZIP_TYPE);

   public GzipTranscoder() {
      super(APPLICATION_GZIP, MediaType.MATCH_ALL);
   }

   @Override
   public Object transcode(Object content, MediaType contentType, MediaType destinationType) {
      if (destinationType.match(APPLICATION_GZIP)) {
         return compress(content);
      } else if (destinationType.match(APPLICATION_OBJECT)) {
         return decompress((byte[]) content);
      } else {
         throw new RuntimeException("Invalid format");
      }
   }

   private byte[] compress(Object o) {
      try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
           GZIPOutputStream gos = new GZIPOutputStream(baos);
           ObjectOutputStream oos = new ObjectOutputStream(gos)) {
         oos.writeObject(o);
         oos.close();
         return baos.toByteArray();
      } catch (IOException e) {
         throw new EncodingException("Failed to encode object", e);
      }
   }

   private Object decompress(byte[] compressed) {
      try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
           GZIPInputStream gis = new GZIPInputStream(bais);
           ObjectInputStream ois = new ObjectInputStream(gis)) {
         return ois.readObject();
      } catch (IOException | ClassNotFoundException e) {
         throw new RuntimeException("Unable to decompress", e);
      }
   }
}
