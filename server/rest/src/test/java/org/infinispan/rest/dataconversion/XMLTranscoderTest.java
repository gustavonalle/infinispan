package org.infinispan.rest.dataconversion;

import static org.infinispan.commons.dataconversion.MediaType.APPLICATION_OBJECT;
import static org.infinispan.commons.dataconversion.MediaType.APPLICATION_XML;
import static org.testng.Assert.assertEquals;

import java.util.Collections;

import org.infinispan.commons.configuration.ClassWhiteList;
import org.infinispan.test.data.Address;
import org.infinispan.test.data.Person;
import org.infinispan.test.dataconversion.AbstractTranscoderTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "rest.XMLTranscoderTest")
public class XMLTranscoderTest extends AbstractTranscoderTest {
   protected Person dataSrc;

   @BeforeTest
   public void setUp() {
      dataSrc = new Person("Joe");
      Address address = new Address();
      address.setCity("London");
      dataSrc.setAddress(address);
      transcoder = new XMLTranscoder(new ClassWhiteList(Collections.singletonList(".*")));
      supportedMediaTypes = transcoder.getSupportedMediaTypes();
   }

   @Override
   public void testTranscoderTranscode() {
      String xmlString = new String((byte[])transcoder.transcode(dataSrc, APPLICATION_OBJECT, APPLICATION_XML));

      Object transcodedBack = transcoder.transcode(xmlString, APPLICATION_XML, APPLICATION_OBJECT);

      assertEquals(dataSrc, transcodedBack, "Must be an equal objects");

   }
}
