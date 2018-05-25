package org.infinispan.commons.dataconversion;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Base64.getUrlDecoder;
import static org.infinispan.commons.dataconversion.MediaType.APPLICATION_OBJECT_TYPE;
import static org.infinispan.commons.dataconversion.MediaType.APPLICATION_WWW_FORM_URLENCODED;
import static org.infinispan.commons.dataconversion.MediaType.TEXT_PLAIN;
import static org.infinispan.commons.dataconversion.MediaType.TEXT_PLAIN_TYPE;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import org.infinispan.commons.logging.Log;
import org.infinispan.commons.logging.LogFactory;
import org.infinispan.commons.marshall.JavaSerializationMarshaller;

/**
 * Utilities to convert between text/plain, octet-stream, java-objects and url-encoded contents.
 *
 * @since 9.2
 */
public final class StandardConversions {

   private static final Log log = LogFactory.getLog(StandardConversions.class);
   
   private static final JavaSerializationMarshaller JAVA_SERIALIZATION_MARSHALLER = new JavaSerializationMarshaller();

   /**
    * Convert text content to a different encoding.
    *
    * @param source The source content.
    * @param sourceType MediaType for the source content.
    * @param destinationType the MediaType of the converted content.
    * @return content conforming to the destination MediaType.
    */
   public static Object convertTextToText(Object source, MediaType sourceType, MediaType destinationType) {
      if (source == null) return null;
      if (sourceType == null || destinationType == null) {
         throw new NullPointerException("MediaType cannot be null!");
      }
      if (!sourceType.match(MediaType.TEXT_PLAIN)) {
         throw log.invalidMediaType(TEXT_PLAIN_TYPE, sourceType.toString());
      }
      Charset sourceCharset = sourceType.getCharset();
      Charset destinationCharset = destinationType.getCharset();
      if (sourceCharset.equals(destinationCharset)) return source;
      byte[] byteContent = source instanceof byte[] ? (byte[]) source : source.toString().getBytes(sourceCharset);
      return convertCharset(byteContent, sourceCharset, destinationCharset);
   }

   /**
    * Converts text content to binary.
    *
    * @param source The source content.
    * @param sourceType MediaType for the source content.
    * @return content converted as octet-stream represented as byte[].
    * @throws EncodingException if the source cannot be interpreted as plain text.
    */
   public static byte[] convertTextToOctetStream(Object source, MediaType sourceType) {
      if (source == null) return null;
      if (sourceType == null) {
         throw new NullPointerException("MediaType cannot be null!");
      }
      if (source instanceof byte[]) return (byte[]) source;
      return source.toString().getBytes(sourceType.getCharset());
   }

   /**
    * Converts text content to the Java representation (String).
    *
    * @param source The source content
    * @param sourceType the MediaType of the source content.
    * @return String representation of the text content.
    * @throws EncodingException if the source cannot be interpreted as plain text.
    */
   public static String convertTextToObject(Object source, MediaType sourceType) {
      if (source == null) return null;
      if (source instanceof String) return source.toString();
      if (source instanceof byte[]) {
         byte[] bytesSource = (byte[]) source;
         return new String(bytesSource, sourceType.getCharset());
      }
      throw log.invalidTextContent(source);
   }

   /**
    * Convert text format to a URL safe format.
    *
    * @param source the source text/plain content.
    * @param sourceType the MediaType of the source content.
    * @return a String with the content URLEncoded.
    * @throws EncodingException if the source format cannot be interpreted as plain/text.
    */
   public static String convertTextToUrlEncoded(Object source, MediaType sourceType) {
      return urlEncode(source, sourceType);
   }

   /**
    * Converts generic byte[] to text.
    *
    * @param source byte[] to convert.
    * @param destination MediaType of the desired text conversion.
    * @return byte[] content interpreted as text, in the encoding specified by the destination MediaType.
    */
   public static byte[] convertOctetStreamToText(byte[] source, MediaType destination) {
      if (source == null) return null;
      return convertCharset(source, UTF_8, destination.getCharset());
   }

   /**
    * Converts an octet stream to a Java object
    *
    * @param source The source to convert
    * @param destination The type of the converted object.
    * @return an instance of a java object compatible with the supplied destination type.
    */
   public static Object convertOctetStreamToJava(byte[] source, MediaType destination) {
      if (source == null) return null;
      if (!destination.match(MediaType.APPLICATION_OBJECT)) {
         throw log.invalidMediaType(APPLICATION_OBJECT_TYPE, destination.toString());
      }
      Optional<String> optType = destination.getParameter("type");
      if (!optType.isPresent()) {
         return source;
      }
      String targetType = optType.get();
      if (targetType.equals("ByteArray")) {
         return source;
      }
      if (targetType.equals(String.class.getName())) {
         return new String(source, UTF_8);
      }
      try {
         return JAVA_SERIALIZATION_MARSHALLER.objectFromByteBuffer(source);
      } catch (IOException | ClassNotFoundException e) {
         throw log.conversionNotSupported(source, MediaType.APPLICATION_OCTET_STREAM_TYPE, destination.toString());
      }
   }

   /**
    * Converts a java object to a sequence of bytes applying standard java serialization.
    *
    * @param source source the java object to convert.
    * @param sourceMediaType the MediaType matching application/x-application-object describing the source.
    * @return byte[] representation of the java object.
    * @throws EncodingException if the sourceMediaType is not a application/x-java-object or if the conversion is
    * not supported.
    */
   public static byte[] convertJavaToOctetStream(Object source, MediaType sourceMediaType) throws IOException, InterruptedException {
      if (source == null) return null;
      if (!sourceMediaType.match(MediaType.APPLICATION_OBJECT)) {
         throw new EncodingException("destination MediaType not conforming to application/x-java-object!");
      }

      Object decoded = decodeObjectContent(source, sourceMediaType);
      if (decoded instanceof byte[]) return (byte[]) decoded;
      if (decoded instanceof String) return ((String) decoded).getBytes(StandardCharsets.UTF_8);
      return JAVA_SERIALIZATION_MARSHALLER.objectToByteBuffer(source);
   }

   /**
    * Converts a java object to a text/plain representation.
    * @param source Object to convert.
    * @param sourceMediaType The MediaType for the source object.
    * @param destinationMediaType The required text/plain specification.
    * @return byte[] with the text/plain representation of the object with the requested charset.
    */
   public static byte[] convertJavaToText(Object source, MediaType sourceMediaType, MediaType destinationMediaType) {
      if (source == null) return null;
      if (sourceMediaType == null || destinationMediaType == null) {
         throw new NullPointerException("sourceMediaType and destinationMediaType cannot be null!");
      }
      Object decoded = decodeObjectContent(source, sourceMediaType);

      if (decoded instanceof byte[]) {
         return convertCharset(source, StandardCharsets.UTF_8, destinationMediaType.getCharset());
      } else {
         String asString = decoded.toString();
         return asString.getBytes(destinationMediaType.getCharset());
      }
   }

   /**
    * Decode UTF-8 as a java object. For this conversion, the "type" parameter is used in the supplied {@link MediaType}.
    *
    * Currently supported types are primitives and String, plus a special "ByteArray" to describe a sequence of bytes.
    *
    * @param content The content to decode.
    * @param contentMediaType the {@link MediaType} describing the content.
    * @return instance of Object according to the supplied MediaType "type" parameter, or if no type is present,
    *         the object itself.
    * @throws EncodingException if the provided type is not supported.
    */
   public static Object decodeObjectContent(Object content, MediaType contentMediaType) {
      try {
         if (content == null) return null;
         if (contentMediaType == null) {
            throw new NullPointerException("contentMediaType cannot be null!");
         }
         String strContent;
         Optional<String> type = contentMediaType.getParameter("type");
         if (!type.isPresent()) {
            return content;
         }
         String sourceType = type.get();
         if (sourceType.equals("ByteArray")) {
            if (content instanceof byte[]) return content;
            if (content instanceof String) return hexToBytes(content.toString());
            throw new EncodingException("Cannot read ByteArray!");
         }

         if (content instanceof byte[]) {
            strContent = new String((byte[]) content, UTF_8);
         } else {
            strContent = content.toString();
         }

         Class<?> destinationType = Class.forName(sourceType);

         if (destinationType == String.class) return content;
         if (destinationType == Boolean.class) return Boolean.parseBoolean(strContent);
         if (destinationType == Short.class) return Short.parseShort(strContent);
         if (destinationType == Byte.class) return Byte.parseByte(strContent);
         if (destinationType == Integer.class) return Integer.parseInt(strContent);
         if (destinationType == Long.class) return Long.parseLong(strContent);
         if (destinationType == Float.class) return Float.parseFloat(strContent);
         if (destinationType == Double.class) return Double.parseDouble(strContent);
         return content;
      } catch (ClassNotFoundException cne) {
         throw new EncodingException("Cannot decode object!", cne);
      }
   }

   /**
    * Convert text content.
    *
    * @param content Object to convert.
    * @param fromCharset Charset of the provided content.
    * @param toCharset Charset to convert to.
    * @return byte[] with the content in the desired charset.
    */
   public static byte[] convertCharset(Object content, Charset fromCharset, Charset toCharset) {
      if (content == null) return null;
      if (fromCharset == null || toCharset == null) {
         throw new NullPointerException("Charset cannot be null!");
      }
      byte[] bytes;
      if (content instanceof String) {
         bytes = content.toString().getBytes(fromCharset);
      } else if (content instanceof byte[]) {
         bytes = (byte[]) content;
      } else {
         bytes = content.toString().getBytes(fromCharset);
      }
      if (fromCharset.equals(toCharset)) return bytes;
      CharBuffer inputContent = fromCharset.decode(ByteBuffer.wrap(bytes));
      ByteBuffer result = toCharset.encode(inputContent);
      return Arrays.copyOf(result.array(), result.limit());
   }

   /**
    * Decode a octet-stream content that is not a byte[]. For this, it uses a special
    * param called "encoding" in the "application/octet-stream" MediaType.
    * The "encoding" param supports only the "hex" value that represents an octet-stream
    * as a hexadecimal representation, for example "0xdeadbeef".
    *
    * In the absence of the "encoding" param, it will assume base64 encoding.
    *
    * @param input Object representing the binary content.
    * @param octetStream The MediaType describing the input.
    * @return a byte[] with the decoded content.
    */
   public static byte[] decodeOctetStream(Object input, MediaType octetStream) {
      if (input == null) {
         throw new NullPointerException("input must not be null");
      }
      if (input instanceof byte[]) return (byte[]) input;
      if (input instanceof String) {
         String encoding = octetStream.getParameter("encoding").orElse("hex");
         String src = input.toString();
         return encoding.equals("hex") ? hexToBytes(src) : getUrlDecoder().decode(src);
      }
      throw new EncodingException("Cannot decode binary content " + input.getClass());
   }

   private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

   public static String bytesToHex(byte[] bytes) {
      if (bytes == null) return null;
      if (bytes.length == 0) return "";
      StringBuilder r = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
         r.append(HEX_DIGITS[b >> 4 & 0x0f]);
         r.append(HEX_DIGITS[b & 0x0f]);
      }
      return "0x" + r.toString();
   }

   private static int forDigit(char digit) {
      if (digit >= '0' && digit <= '9') return digit - 48;
      if (digit == 'a') return 10;
      if (digit == 'b') return 11;
      if (digit == 'c') return 12;
      if (digit == 'd') return 13;
      if (digit == 'e') return 14;
      if (digit == 'f') return 15;
      throw new EncodingException("Invalid digit found in hex format!");
   }

   public static byte[] hexToBytes(String hex) {
      if (hex == null) return null;
      if (hex.isEmpty()) return new byte[]{};
      if (!hex.startsWith("0x") || hex.length() % 2 != 0) {
         throw new EncodingException("Illegal hex literal!");
      }
      byte[] result = new byte[(hex.length() - 2) / 2];
      for (int i = 2; i < hex.length(); i += 2) {
         int msb = forDigit(hex.charAt(i));
         int lsb = forDigit(hex.charAt(i + 1));
         byte b = (byte) (msb * 16 + lsb);
         result[(i - 2) / 2] = b;

      }
      return result;
   }

   /**
    * Handle x-www-form-urlencoded as single values for now.
    * Ideally it should generate a Map<String, String>
    */
   public static Object convertUrlEncodedToObject(Object content) {
      Object decoded = urlDecode(content);
      return convertTextToObject(decoded, TEXT_PLAIN);
   }

   public static Object convertUrlEncodedToText(Object content, MediaType destinationType) {
      return convertTextToText(urlDecode(content), TEXT_PLAIN, destinationType);
   }

   public static Object convertUrlEncodedToOctetStream(Object content) {
      return convertTextToOctetStream(urlDecode(content), TEXT_PLAIN);
   }

   public static String urlEncode(Object content, MediaType mediaType) {
      if (content == null) return null;
      try {
         String asString;
         if (content instanceof byte[]) {
            asString = new String((byte[]) content, UTF_8);
         } else {
            asString = content.toString();
         }
         return URLEncoder.encode(asString, mediaType.getCharset().toString());
      } catch (UnsupportedEncodingException e) {
         throw log.errorEncoding(content, APPLICATION_WWW_FORM_URLENCODED);
      }
   }

   public static Object urlDecode(Object content) {
      try {
         if (content == null) return null;
         if (content instanceof byte[]) {
            byte[] bytesSource = (byte[]) content;
            return URLDecoder.decode(new String(bytesSource, UTF_8), UTF_8.toString());
         }
         return URLDecoder.decode(content.toString(), UTF_8.toString());
      } catch (UnsupportedEncodingException e) {
         throw log.cannotDecodeFormURLContent(content);
      }
   }

   public static Object convertOctetStreamToUrlEncoded(Object content, MediaType contentType) {
      byte[] decoded = decodeOctetStream(content, contentType);
      return urlEncode(decoded, MediaType.TEXT_PLAIN);
   }
}
