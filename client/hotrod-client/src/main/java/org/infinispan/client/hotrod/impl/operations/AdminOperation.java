package org.infinispan.client.hotrod.impl.operations;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.infinispan.client.hotrod.configuration.Configuration;
import org.infinispan.client.hotrod.impl.protocol.Codec;
import org.infinispan.client.hotrod.impl.protocol.HeaderParams;
import org.infinispan.client.hotrod.impl.transport.Transport;
import org.infinispan.client.hotrod.impl.transport.TransportFactory;

/**
 * AdminOperation. A special type of {@link ExecuteOperation} which returns the result of an admin operation which is always
 * represented as a JSON object. The actual parsing and interpretation of the result is up to the caller.
 *
 * @author Tristan Tarrant
 * @since 9.3
 */
public class AdminOperation extends ExecuteOperation<String> {
   protected AdminOperation(Codec codec, TransportFactory transportFactory, byte[] cacheName, AtomicInteger topologyId, int flags, Configuration cfg, String taskName, Map<String, byte[]> marshalledParams) {
      super(codec, transportFactory, cacheName, topologyId, flags, cfg, taskName, marshalledParams);
   }

   @Override
   protected String executeOperation(Transport transport) {
      HeaderParams params = writeHeader(transport, EXEC_REQUEST);
      transport.writeString(taskName);
      transport.writeVInt(marshalledParams.size());
      for(Map.Entry<String, byte[]> entry : marshalledParams.entrySet()) {
         transport.writeString(entry.getKey());
         transport.writeArray(entry.getValue());
      }
      transport.flush();
      readHeaderAndValidate(transport, params);
      byte[] bytes = transport.readArray();
      return new String(bytes, StandardCharsets.UTF_8);
   }
}
