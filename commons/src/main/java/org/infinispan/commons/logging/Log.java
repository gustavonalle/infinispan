package org.infinispan.commons.logging;

import static org.jboss.logging.Logger.Level.ERROR;
import static org.jboss.logging.Logger.Level.WARN;

import org.infinispan.commons.CacheConfigurationException;
import org.infinispan.commons.CacheException;
import org.infinispan.commons.dataconversion.EncodingException;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.counter.exception.CounterException;
import org.infinispan.counter.exception.CounterOutOfBoundsException;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * Infinispan's log abstraction layer on top of JBoss Logging.
 * <p/>
 * It contains explicit methods for all INFO or above levels so that they can
 * be internationalized. For the commons module, message ids ranging from 0901
 * to 1000 inclusively have been reserved.
 * <p/>
 * <code> Log log = LogFactory.getLog( getClass() ); </code> The above will get
 * you an instance of <tt>Log</tt>, which can be used to generate log messages
 * either via JBoss Logging which then can delegate to Log4J (if the libraries
 * are present) or (if not) the built-in JDK logger.
 * <p/>
 * In addition to the 6 log levels available, this framework also supports
 * parameter interpolation, similar to the JDKs {@link String#format(String, Object...)}
 * method. What this means is, that the following block:
 * <code> if (log.isTraceEnabled()) { log.trace("This is a message " + message + " and some other value is " + value); }
 * </code>
 * <p/>
 * ... could be replaced with ...
 * <p/>
 * <code> if (log.isTraceEnabled()) log.tracef("This is a message %s and some other value is %s", message, value);
 * </code>
 * <p/>
 * This greatly enhances code readability.
 * <p/>
 * If you are passing a <tt>Throwable</tt>, note that this should be passed in
 * <i>before</i> the vararg parameter list.
 * <p/>
 *
 * @author Manik Surtani
 * @since 4.0
 * @private
 */
@MessageLogger(projectCode = "ISPN")
public interface Log extends BasicLogger {
   @LogMessage(level = WARN)
   @Message(value = "Property %s could not be replaced as intended!", id = 901)
   void propertyCouldNotBeReplaced(String line);

   @LogMessage(level = WARN)
   @Message(value = "Invocation of %s threw an exception %s. Exception is ignored.", id = 902)
   void ignoringException(String methodName, String exceptionName, @Cause Throwable t);

   @LogMessage(level = ERROR)
   @Message(value = "Unable to set value!", id = 903)
   void unableToSetValue(@Cause Exception e);

   @Message(value = "Error while initializing SSL context", id = 904)
   CacheConfigurationException sslInitializationException(@Cause Throwable e);

   @LogMessage(level = ERROR)
   @Message(value = "Unable to load %s from any of the following classloaders: %s", id = 905)
   void unableToLoadClass(String classname, String classloaders, @Cause Throwable cause);

   @LogMessage(level = WARN)
   @Message(value = "Unable to convert string property [%s] to an int! Using default value of %d", id = 906)
   void unableToConvertStringPropertyToInt(String value, int defaultValue);

   @LogMessage(level = WARN)
   @Message(value = "Unable to convert string property [%s] to a long! Using default value of %d", id = 907)
   void unableToConvertStringPropertyToLong(String value, long defaultValue);

   @LogMessage(level = WARN)
   @Message(value = "Unable to convert string property [%s] to a boolean! Using default value of %b", id = 908)
   void unableToConvertStringPropertyToBoolean(String value, boolean defaultValue);

   @Message(value = "Unwrapping %s to a type of %s is not a supported", id = 909)
   IllegalArgumentException unableToUnwrap(Object o, Class<?> clazz);

   @Message(value = "Illegal value for thread pool parameter(s) %s, it should be: %s", id = 910)
   CacheConfigurationException illegalValueThreadPoolParameter(String parameter, String requirement);

   @Message(value = "Unwrapping of any instances in %s to a type of %s is not a supported", id = 911)
   IllegalArgumentException unableToUnwrapAny(String objs, Class<?> clazz);

   @Message(value = "Expecting a protected configuration for %s", id = 912)
   IllegalStateException unprotectedAttributeSet(String name);

   @Message(value = "Expecting a unprotected configuration for %s", id = 913)
   IllegalStateException protectedAttributeSet(String name);

   @Message(value = "Duplicate attribute '%s' in attribute set '%s'", id = 914)
   IllegalArgumentException attributeSetDuplicateAttribute(String name, String setName);

   @Message(value = "No such attribute '%s' in attribute set '%s'", id = 915)
   IllegalArgumentException noSuchAttribute(String name, String setName);

   @Message(value = "No attribute copier for type '%s'", id = 916)
   IllegalArgumentException noAttributeCopierForType(Class<?> klass);

   @Message(value = "Cannot resize unbounded container", id = 917)
   UnsupportedOperationException cannotResizeUnboundedContainer();

   @Message(value = "The alias '%s' does not exist in the key store '%s'", id = 918)
   SecurityException noSuchAliasInKeyStore(String keyAlias, String keyStoreFileName);

   @Message(value = "MediaType cannot be empty or null!", id = 929)
   EncodingException missingMediaType();

   @Message(value = "MediaType must contain a type and a subtype separated by '/'", id = 930)
   EncodingException invalidMediaTypeSubtype();

   @Message(value = "Failed to parse MediaType: Invalid param description '%s'", id = 931)
   EncodingException invalidMediaTypeParam(String param);

   @Message(value = "Unclosed param value quote", id = 932)
   EncodingException unquotedMediaTypeParam();

   @Message(value = "Invalid character '%s' found in token '%s'", id = 933)
   EncodingException invalidCharMediaType(char character, String token);

   @Message(value = "Unsupported content '%s'", id = 934)
   EncodingException unsupportedContent(Object content);

   @Message(value = "Invalid Weight '%s'. Supported values are between 0 and 1.0", id = 935)
   EncodingException invalidWeight(Object weight);

   @Message(value = "Class '%s' blocked by deserialization white list. Adjust the configuration serialization white list regular expression to include this class.", id = 936)
   CacheException classNotInWhitelist(String className);

   //----- counters exceptions ------

   @Message(value = CounterOutOfBoundsException.FORMAT_MESSAGE, id = 28001)
   CounterOutOfBoundsException counterOurOfBounds(String bound);

   @Message(value = "Invalid counter type. Expected=%s but got %s", id = 28014)
   CounterException invalidCounterType(String expected, String actual);

   @Message(value = "Counter '%s' is not defined.", id = 28016)
   CounterException undefinedCounter(String name);

   @Message(value = "WEAK and BOUNDED encoded flag isn't supported!", id = 28022)
   CounterException invalidCounterTypeEncoded();

   //----- counters exceptions ------

   @Message(value = "Invalid media type. Expected '%s' but got '%s'", id = 28024)
   EncodingException invalidMediaType(String expected, String actual);

   @Message(value = "Invalid text content '%s'", id = 28025)
   EncodingException invalidTextContent(Object content);

   @Message(value = "Conversion of content '%s' from '%s' to '%s' not supported", id = 28026)
   EncodingException conversionNotSupported(Object content, String fromMediaType, String toMediaType);

   @Message(value = "Invalid application/x-www-form-urlencoded content: '%s'", id = 28027)
   EncodingException cannotDecodeFormURLContent(Object content);

   @Message(value = "Error encoding content '%s' to '%s'", id = 28028)
   EncodingException errorEncoding(Object content, MediaType mediaType);

   @LogMessage(level = WARN)
   @Message(value = "Unable to convert property [%s] to an enum! Using default value of %d", id = 28023)
   void unableToConvertStringPropertyToEnum(String value, String defaultValue);
}
