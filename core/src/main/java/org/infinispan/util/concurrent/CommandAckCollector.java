package org.infinispan.util.concurrent;

import org.infinispan.commands.CommandInvocationId;
import org.infinispan.commons.util.Util;
import org.infinispan.remoting.transport.Address;
import org.infinispan.statetransfer.OutdatedTopologyException;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * An acknowledge collector for Triangle algorithm used in non-transactional caches for write operations.
 * <p>
 * Acknowledges are used between the owners and the originator. They signal the completion of a write operation. The
 * operation can complete successfully or not.
 * <p>
 * The acknowledges are valid on the same cache topology id. So, each acknowledge is tagged with the command topology
 * id. Acknowledges from previous topology id are discarded.
 * <p>
 * The acknowledges from the primary owner carry the return value of the operation.
 *
 * @author Pedro Ruivo
 * @since 9.0
 */
public class CommandAckCollector {

   private static final Log log = LogFactory.getLog(CommandAckCollector.class);
   private static final boolean trace = log.isTraceEnabled();

   private final ConcurrentHashMap<CommandInvocationId, Collector<?>> collectorMap;

   public CommandAckCollector() {
      collectorMap = new ConcurrentHashMap<>();
   }

   private static <T> T awaitFuture(CompletableFuture<T> future, long timeout, TimeUnit timeUnit) throws Throwable {
      try {
         return future.get(timeout, timeUnit);
      } catch (ExecutionException e) {
         throw e.getCause();
      } catch (java.util.concurrent.TimeoutException e) {
         throw log.timeoutWaitingForAcks(Util.prettyPrintTime(timeout, timeUnit));
      }
   }

   /**
    * Creates a collector for a single key write operation.
    *
    * @param id         the {@link CommandInvocationId}.
    * @param owners     the owners of the key. It assumes the first element as primary owner.
    * @param topologyId the current topology id.
    */
   public void create(CommandInvocationId id, Collection<Address> owners, int topologyId) {
      collectorMap.putIfAbsent(id, new SingleKeyCollector(id, owners, topologyId));
      if (trace) {
         log.tracef("Created new collector for %s. Owners=%s", id, owners);
      }
   }

   /**
    * Creates a collector for a single key write operation.
    * <p>
    * It should be used when the primary owner is the local node and the return value and its acknowledge is already
    * known.
    *
    * @param id          the {@link CommandInvocationId}.
    * @param returnValue the primary owner result.
    * @param owners      the owners of the key. It assumes the first element as primary owner.
    * @param topologyId  the current topology id.
    */
   public void create(CommandInvocationId id, Object returnValue, Collection<Address> owners, int topologyId) {
      collectorMap.putIfAbsent(id, new SingleKeyCollector(id, returnValue, owners, topologyId));
      if (trace) {
         log.tracef("Created new collector for %s. ReturnValue=%s. Owners=%s", id, returnValue, owners);
      }
   }

   /**
    * Creates a collector for {@link org.infinispan.commands.write.PutMapCommand}.
    *
    * @param id         the {@link CommandInvocationId}.
    * @param primary    a map between a primary owner and its segments affected.
    * @param backups    a map between a backup owner and its segments affected.
    * @param topologyId the current topology id.
    */
   public void createPutMapCollector(CommandInvocationId id, Map<Address, Collection<Integer>> primary, Map<Address, Collection<Integer>> backups, int topologyId) {
      collectorMap.putIfAbsent(id, new PutMapCollector(id, primary, backups, topologyId));
      if (trace) {
         log.tracef("Created new collector for %s. PrimarySegments=%s. BackupSegments", id, primary, backups);
      }
   }

   /**
    * Acknowledges a {@link org.infinispan.commands.write.PutMapCommand} completion in the primary owner.
    *
    * @param id          the {@link CommandInvocationId}.
    * @param from        the primary owner.
    * @param segments    the segments affected and acknowledged.
    * @param returnValue the return value.
    * @param topologyId  the topology id.
    */
   public void putMapPrimaryAck(CommandInvocationId id, Address from, Collection<Integer> segments, Map<Object, Object> returnValue, int topologyId) {
      PutMapCollector collector = (PutMapCollector) collectorMap.get(id);
      if (collector != null) {
         collector.primaryAck(returnValue, from, segments, topologyId);
      }
   }

   /**
    * Acknowledges a {@link org.infinispan.commands.write.PutMapCommand} completion in the backup owner.
    *
    * @param id         the {@link CommandInvocationId}.
    * @param from       the backup owner.
    * @param segments   the segments affected and acknowledged.
    * @param topologyId the topology id.
    */
   public void putMapBackupAck(CommandInvocationId id, Address from, Collection<Integer> segments, int topologyId) {
      PutMapCollector collector = (PutMapCollector) collectorMap.get(id);
      if (collector != null) {

         collector.backupAck(from, segments, topologyId);
      }
   }

   /**
    * Acknowledges a write operation completion in the backup owner.
    *
    * @param id         the {@link CommandInvocationId}.
    * @param from       the backup owner.
    * @param topologyId the topology id.
    */
   public void backupAck(CommandInvocationId id, Address from, int topologyId) {
      SingleKeyCollector collector = (SingleKeyCollector) collectorMap.get(id);
      if (collector != null) {
         collector.backupAck(topologyId, from);
      }
   }

   /**
    * Acknowledges a write operation completion in the primary owner.
    * <p>
    * If the operation does not succeed (conditional commands), the collector is completed without waiting for the
    * acknowledges from the backup owners.
    *
    * @param id          the {@link CommandInvocationId}.
    * @param returnValue the return value.
    * @param success     {@code true} if the operation succeed in the primary owner, {@code false} otherwise.
    * @param from        the primary owner.
    * @param topologyId  the topology id.
    */
   public void primaryAck(CommandInvocationId id, Object returnValue, boolean success, Address from, int topologyId) {
      SingleKeyCollector collector = (SingleKeyCollector) collectorMap.get(id);
      if (collector != null) {
         collector.primaryAck(topologyId, returnValue, success, from);
      }
   }

   /**
    * Acknowledges an exception during the operation execution.
    * <p>
    * The collector is completed without waiting any further acknowledges.
    *
    * @param id         the {@link CommandInvocationId}.
    * @param throwable  the {@link Throwable}.
    * @param topologyId the topology id.
    */
   public void completeExceptionally(CommandInvocationId id, Throwable throwable, int topologyId) {
      Collector<?> collector = collectorMap.get(id);
      if (collector != null) {
         collector.completeExceptionally(throwable, topologyId);
      }
   }

   /**
    * Waits for all the acknowledges from the primary and backup owners affected by the operation.
    * <p>
    * The collector is removed after completion or timeout.
    *
    * @param id          the {@link CommandInvocationId}.
    * @param timeout     the maximum time to wait.
    * @param timeUnit    the time unit of the timeout argument.
    * @param returnValue the local operation's return value.
    * @param <T>         the return value type.
    * @return the operation return value from the primary owner or {@code returnValue} if the collector does not exists.
    * @throws Throwable an exception if an error occurred or a timeout happens.
    */
   public <T> T awaitCollector(CommandInvocationId id, long timeout, TimeUnit timeUnit, T returnValue) throws Throwable {
      //noinspection unchecked
      Collector<T> collector = (Collector<T>) collectorMap.get(id);
      if (collector != null) {
         try {
            if (trace) {
               log.tracef("[Collector#%s] Waiting for acks.", id);
            }
            return awaitFuture(collector.getFuture(), timeout, timeUnit);
         } catch (CompletionException e) {
            throw CompletableFutures.extractException(e);
         } finally {
            collectorMap.remove(id);
         }
      }
      return returnValue;
   }

   /**
    * @param id the {@link CommandInvocationId}.
    * @return the {@link CompletableFuture} associated with the command's collector or {@code null} if it doesn't exist.
    */
   public CompletableFuture<?> get(CommandInvocationId id) {
      Collector<?> collector = collectorMap.get(id);
      return collector == null ? null : collector.getFuture();
   }

   /**
    * @return the pending {@link CommandInvocationId} (testing purposes only)
    */
   public List<CommandInvocationId> getPendingCommands() {
      return new ArrayList<>(collectorMap.keySet());
   }

   /**
    * Same as {@link #awaitCollector(CommandInvocationId, long, TimeUnit, Object)} but without removing the collector.
    * (testing purposes only)
    */
   public void awaitWithoutRemoving(CommandInvocationId id, long timeout, TimeUnit timeUnit) throws Throwable {
      Collector<?> collector = collectorMap.get(id);
      if (collector != null) {
         awaitFuture(collector.getFuture(), timeout, timeUnit);
      }
   }

   /**
    * @param id the command id.
    * @return {@code true} if there are acknowledges pending from the backup owners, {@code false} otherwise. (testing
    * purposes only)
    */
   public boolean hasPendingBackupAcks(CommandInvocationId id) {
      Collector<?> collector = collectorMap.get(id);
      return collector != null && collector.hasPendingBackupAcks();
   }

   /**
    * Notifies a change in member list.
    *
    * @param members the new cluster members.
    */
   public void onMembersChange(Collection<Address> members) {
      Set<Address> currentMembers = new HashSet<>(members);
      for (Collector<?> collector : collectorMap.values()) {
         collector.onMembersChange(currentMembers);
      }
   }

   /**
    * Removes the collector associated with the command.
    *
    * @param id the {@link CommandInvocationId}.
    */
   public void dispose(CommandInvocationId id) {
      if (trace) {
         log.tracef("[Collector#%s] Dispose collector.", id);
      }
      collectorMap.remove(id);
   }

   private interface Collector<T> {
      void completeExceptionally(Throwable throwable, int topologyId);

      boolean hasPendingBackupAcks();

      CompletableFuture<T> getFuture();

      void onMembersChange(Collection<Address> members);
   }

   private static class SingleKeyCollector implements Collector<Object> {
      private final CommandInvocationId id;
      private final CompletableFuture<Object> future;
      private final Collection<Address> owners;
      private final Address primaryOwner;
      private final int topologyId;
      private Object returnValue;

      private SingleKeyCollector(CommandInvocationId id, Collection<Address> owners, int topologyId) {
         this.id = id;
         this.primaryOwner = owners.iterator().next();
         this.topologyId = topologyId;
         this.future = new CompletableFuture<>();
         this.owners = new HashSet<>(owners); //removal is fast
      }

      private SingleKeyCollector(CommandInvocationId id, Object returnValue, Collection<Address> owners, int topologyId) {
         this.id = id;
         this.returnValue = returnValue;
         this.primaryOwner = owners.iterator().next();
         this.topologyId = topologyId;
         Collection<Address> tmpOwners = new HashSet<>(owners);
         tmpOwners.remove(primaryOwner);
         if (tmpOwners.isEmpty()) { //num owners is 1 or single member in cluster
            this.owners = Collections.emptyList();
            this.future = CompletableFuture.completedFuture(returnValue);
         } else {
            this.future = new CompletableFuture<>();
            this.owners = tmpOwners;
         }
      }

      public synchronized void primaryAck(int topologyId, Object returnValue, boolean success, Address from) {
         if (trace) {
            log.tracef("[Collector#%s] Primary ACK. Success=%s. ReturnValue=%s. Address=%s, TopologyId=%s (expected=%s)",
                       id, success, returnValue, from, topologyId, this.topologyId);
         }
         if (this.topologyId != topologyId || !owners.remove(from)) {
            //already received!
            return;
         }
         this.returnValue = returnValue;

         if (!success) {
            //we are not receiving any backups ack!
            owners.clear();
            future.complete(returnValue);
            if (trace) {
               log.tracef("[Collector#%s] Ready! Command not succeed on primary.", id);
            }
         } else if (owners.isEmpty()) {
            markReady();
         }
      }

      public synchronized void backupAck(int topologyId, Address from) {
         if (trace) {
            log.tracef("[Collector#%s] Backup ACK. Address=%s, TopologyId=%s (expected=%s)",
                       id, from, topologyId, this.topologyId);
         }
         if (this.topologyId == topologyId && owners.remove(from) && owners.isEmpty()) {
            markReady();
         }
      }

      @Override
      public synchronized void completeExceptionally(Throwable throwable, int topologyId) {
         if (trace) {
            log.tracef(throwable, "[Collector#%s] completed exceptionally. TopologyId=%s (expected=%s)",
                       id, topologyId, this.topologyId);
         }
         if (this.topologyId != topologyId) {
            return;
         }
         doCompleteExceptionally(throwable);
      }

      private void markReady() {
         if (trace) {
            log.tracef("[Collector#%s] Ready! Return value=%ss.", id, returnValue);
         }
         future.complete(returnValue);
      }

      private void doCompleteExceptionally(Throwable throwable) {
         owners.clear();
         future.completeExceptionally(throwable);
      }

      @Override
      public synchronized boolean hasPendingBackupAcks() {
         return owners.size() > 1 || //at least one backup + primary address
               //if one is missing, make sure that it isn't the primary
               owners.size() == 1 && !primaryOwner.equals(owners.iterator().next());
      }

      @Override
      public CompletableFuture<Object> getFuture() {
         return future;
      }

      @Override
      public synchronized void onMembersChange(Collection<Address> members) {
         if (!members.contains(primaryOwner)) {
            //primary owner left. throw OutdatedTopologyException to trigger a retry
            if (trace) {
               log.tracef("[Collector#%s] The Primary Owner left the cluster.", id);
            }
            doCompleteExceptionally(OutdatedTopologyException.getCachedInstance());
         } else if (owners.retainAll(members) && owners.isEmpty()) {
            if (trace) {
               log.tracef("[Collector#%s] Some backups left the cluster.", id);
            }
            markReady();
         }
      }
   }

   private static class PutMapCollector implements Collector<Map<Object, Object>> {
      private final CommandInvocationId id;
      private final Map<Object, Object> returnValue;
      private final Map<Address, Collection<Integer>> primary;
      private final Map<Address, Collection<Integer>> backups;
      private final CompletableFuture<Map<Object, Object>> future;
      private final int topologyId;

      public PutMapCollector(CommandInvocationId id, Map<Address, Collection<Integer>> primary, Map<Address, Collection<Integer>> backups, int topologyId) {
         this.id = id;
         this.topologyId = topologyId;
         this.returnValue = new HashMap<>();
         this.backups = backups;
         this.primary = primary;
         future = new CompletableFuture<>();
      }

      public synchronized void primaryAck(Map<Object, Object> returnValue, Address from, Collection<Integer> segments, int topologyId) {
         if (trace) {
            log.tracef("[Collector#%s] PutMap Primary ACK. Address=%s. TopologyId=%s (expected=%s). Segments=%s",
                       id, from, topologyId, this.topologyId, segments);
         }
         if (this.topologyId != topologyId) {
            return;
         }
         this.returnValue.putAll(returnValue);
         ack(primary, from, segments);
      }

      public synchronized void backupAck(Address from, Collection<Integer> segments, int topologyId) {
         if (trace) {
            log.tracef("[Collector#%s] PutMap Backup ACK. Address=%s. TopologyId=%s (expected=%s). Segments=%s",
                       id, from, topologyId, this.topologyId, segments);
         }
         if (this.topologyId != topologyId) {
            return;
         }
         ack(backups, from, segments);
      }

      private void ack(Map<Address, Collection<Integer>> map, Address from, Collection<Integer> segments) {
         Collection<Integer> pendingSegments = map.getOrDefault(from, Collections.emptyList());
         if (pendingSegments.removeAll(segments) && pendingSegments.isEmpty()) {
            map.remove(from);
            checkCompleted();
         }
      }

      private void checkCompleted() {
         if (primary.isEmpty() && backups.isEmpty()) {
            if (trace) {
               log.tracef("[Collector#%s] Ready! Return value=%ss.", id, returnValue);
            }
            future.complete(returnValue);
         }
      }

      private void doCompleteExceptionally(Throwable throwable) {
         returnValue.clear();
         primary.clear();
         backups.clear();
         future.completeExceptionally(throwable);
      }

      public synchronized void completeExceptionally(Throwable throwable, int topologyId) {
         if (trace) {
            log.tracef(throwable, "[Collector#%s] completed exceptionally. TopologyId=%s (expected=%s)",
                       id, topologyId, this.topologyId);
         }
         if (this.topologyId != topologyId) {
            return;
         }
         doCompleteExceptionally(throwable);
      }


      @Override
      public synchronized boolean hasPendingBackupAcks() {
         return !backups.isEmpty();
      }


      @Override
      public CompletableFuture<Map<Object, Object>> getFuture() {
         return future;
      }

      @Override
      public void onMembersChange(Collection<Address> members) {
         if (!members.containsAll(primary.keySet())) {
            //primary owner left. throw OutdatedTopologyException to trigger a retry
            if (trace) {
               log.tracef("[Collector#%s] A primary Owner left the cluster.", id);
            }
            doCompleteExceptionally(OutdatedTopologyException.getCachedInstance());
         } else if (backups.keySet().retainAll(members)) {
            if (trace) {
               log.tracef("[Collector#%s] Some backups left the cluster.", id);
            }
            checkCompleted();
         }
      }


   }
}
