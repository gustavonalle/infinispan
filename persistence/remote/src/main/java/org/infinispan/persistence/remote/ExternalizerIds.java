package org.infinispan.persistence.remote;

/**
 * 1800 to 1899 range is reserved for this module
 *
 * @author gustavonalle
 * @since 8.2
 */
public interface ExternalizerIds {

   Integer MIGRATION_TASK = 1900;
   Integer REMOVED_FILTER = 1901;
   Integer ENTRY_WRITER = 1902;
   Integer DISCONNECT_REMOTE_STORE = 1903;
}
