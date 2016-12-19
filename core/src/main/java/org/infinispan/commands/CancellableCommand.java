package org.infinispan.commands;

import java.util.UUID;

import org.infinispan.commands.remote.CacheRpcCommand;

/**
 * CancellableCommand is a command whose execution in remote VM can be canceled (if needed)
 *
 * @author Vladimir Blagojevic
 * @deprecated Since 8.3, will be removed.
 */
@Deprecated
public interface CancellableCommand extends CacheRpcCommand {

   /**
    * Returns UUID of a command
    *
    * @return command UUID
    */
   UUID getUUID();

}
