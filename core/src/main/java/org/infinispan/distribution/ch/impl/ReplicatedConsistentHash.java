package org.infinispan.distribution.ch.impl;

import org.infinispan.commons.hash.Hash;
import org.infinispan.commons.marshall.InstanceReusingAdvancedExternalizer;
import org.infinispan.commons.util.Util;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.globalstate.ScopedPersistentState;
import org.infinispan.marshall.core.Ids;
import org.infinispan.remoting.transport.Address;
import org.infinispan.remoting.transport.jgroups.JGroupsAddressCache;
import org.infinispan.topology.PersistentUUID;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Special implementation of {@link org.infinispan.distribution.ch.ConsistentHash} for replicated caches.
 * The hash-space has several segments owned by all members and the primary ownership of each segment is evenly
 * spread between members.
 *
 * @author Dan Berindei
 * @author anistor@redhat.com
 * @since 5.2
 */
public class ReplicatedConsistentHash implements ConsistentHash {

   private static final String STATE_PRIMARY_OWNERS = "primaryOwners.%d";
   private static final String STATE_PRIMARY_OWNERS_COUNT = "primaryOwners";
   private final Hash hashFunction;
   private final int[] primaryOwners;
   private final List<Address> members;
   private final Set<Address> membersSet;
   private final Set<Integer> segments;
   private final int segmentSize;

   public ReplicatedConsistentHash(Hash hashFunction, List<Address> members, int[] primaryOwners) {
      this.hashFunction = hashFunction;
      this.members = Collections.unmodifiableList(new ArrayList<>(members));
      this.membersSet = Collections.unmodifiableSet(new HashSet<>(members));
      this.primaryOwners = primaryOwners;
      segments = computeSegments(primaryOwners);
      segmentSize = Util.getSegmentSize(primaryOwners.length);
   }

   private Set<Integer> computeSegments(int[] primaryOwners) {
      Set<Integer> segmentIds = new HashSet<>(primaryOwners.length);
      for (int i = 0; i < primaryOwners.length; i++) {
         segmentIds.add(i);
      }
      return Collections.unmodifiableSet(segmentIds);
   }

   public ReplicatedConsistentHash union(ReplicatedConsistentHash ch2) {
      if (!this.getHashFunction().equals(ch2.getHashFunction())) {
         throw new IllegalArgumentException("The consistent hash objects must have the same hash function");
      }
      if (this.getNumSegments() != ch2.getNumSegments()) {
         throw new IllegalArgumentException("The consistent hash objects must have the same number of segments");

      }

      List<Address> unionMembers = new ArrayList<Address>(this.getMembers());
      for (Address member : ch2.getMembers()) {
         if (!unionMembers.contains(member)) {
            unionMembers.add(member);
         }
      }

      int[] primaryOwners = new int[this.getNumSegments()];
      for (int segmentId = 0; segmentId < primaryOwners.length; segmentId++) {
         Address primaryOwner = this.locatePrimaryOwnerForSegment(segmentId);
         int primaryOwnerIndex = unionMembers.indexOf(primaryOwner);
         primaryOwners[segmentId] = primaryOwnerIndex;
      }

      return new ReplicatedConsistentHash(this.getHashFunction(), unionMembers, primaryOwners);
   }
 
   ReplicatedConsistentHash(ScopedPersistentState state) {
      this.hashFunction = Util.getInstance(state.getProperty(ConsistentHashPersistenceConstants.STATE_HASH_FUNCTION), null);
      int numMembers = Integer.parseInt(state.getProperty(ConsistentHashPersistenceConstants.STATE_MEMBERS));
      this.members = new ArrayList<>(numMembers);
      for(int i = 0; i < numMembers; i++) {
         PersistentUUID uuid = PersistentUUID.fromString(state.getProperty(String.format(ConsistentHashPersistenceConstants.STATE_MEMBER, i)));
         Address address = JGroupsAddressCache.fromPersistentUUID(uuid);
         if (address == null) {
            throw new IllegalStateException("Unknown address for "+uuid);
         }
         this.members.add(address);
      }
      this.membersSet = Collections.unmodifiableSet(new HashSet<>(this.members));
      int numPrimaryOwners = state.getIntProperty(STATE_PRIMARY_OWNERS_COUNT);
      this.primaryOwners = new int[numPrimaryOwners];
      for (int i = 0; i < numPrimaryOwners; i++) {
         this.primaryOwners[i] = state.getIntProperty(String.format(STATE_PRIMARY_OWNERS, i));
      }
      segments = computeSegments(primaryOwners);
      segmentSize = Util.getSegmentSize(primaryOwners.length);
   }

   @Override
   public int getNumSegments() {
      return primaryOwners.length;
   }

   @Override
   public int getNumOwners() {
      return members.size();
   }

   @Override
   public List<Address> getMembers() {
      return members;
   }

   @Override
   public Hash getHashFunction() {
      return hashFunction;
   }

   @Override
   public int getSegment(Object key) {
      // The result must always be positive, so we make sure the dividend is positive first
      return (hashFunction.hash(key) & Integer.MAX_VALUE) / segmentSize;
   }

   @Override
   public List<Address> locateOwnersForSegment(int segmentId) {
      Address primaryOwner = locatePrimaryOwnerForSegment(segmentId);
      List<Address> owners = new ArrayList<Address>(members.size());
      owners.add(primaryOwner);
      for (Address member : members) {
         if (!member.equals(primaryOwner)) {
            owners.add(member);
         }
      }
      return owners;
   }

   @Override
   public Address locatePrimaryOwnerForSegment(int segmentId) {
      return members.get(primaryOwners[segmentId]);
   }

   @Override
   public Set<Integer> getSegmentsForOwner(Address owner) {
      if (owner == null) {
         throw new IllegalArgumentException("owner cannot be null");
      }
      if (!membersSet.contains(owner)) {
         throw new IllegalArgumentException("The node is not a member : " + owner);
      }
      return segments;
   }

   @Override
   public Set<Integer> getPrimarySegmentsForOwner(Address owner) {
      int index = members.indexOf(owner);
      if (index == -1) {
         throw new IllegalArgumentException("The node is not a member : " + owner);
      }
      Set<Integer> primarySegments = new HashSet<Integer>();
      for (int i = 0; i < primaryOwners.length; ++i) {
         if (primaryOwners[i] == index) {
            primarySegments.add(i);
         }
      }
      return primarySegments;
   }

   @Override
   public String getRoutingTableAsString() {
      return Arrays.toString(primaryOwners);
   }

   @Override
   public Address locatePrimaryOwner(Object key) {
      return locatePrimaryOwnerForSegment(getSegment(key));
   }

   @Override
   public List<Address> locateOwners(Object key) {
      return locateOwnersForSegment(getSegment(key));
   }

   @Override
   public Set<Address> locateAllOwners(Collection<Object> keys) {
      return membersSet;
   }

   @Override
   public boolean isKeyLocalToNode(Address nodeAddress, Object key) {
      return isSegmentLocalToNode(nodeAddress, 0);
   }

   @Override
   public boolean isSegmentLocalToNode(Address nodeAddress, int segmentId) {
      return membersSet.contains(nodeAddress);
   }

   @Override

   public boolean isReplicated() {
      return true;
   }

   public void toScopedState(ScopedPersistentState state) {
      state.setProperty(ConsistentHashPersistenceConstants.STATE_CONSISTENT_HASH, this.getClass().getName());
      state.setProperty(ConsistentHashPersistenceConstants.STATE_HASH_FUNCTION, hashFunction.getClass().getName());
      state.setProperty(ConsistentHashPersistenceConstants.STATE_MEMBERS, Integer.toString(members.size()));
      for (int i = 0; i < members.size(); i++) {
         state.setProperty(String.format(ConsistentHashPersistenceConstants.STATE_MEMBER, i),
               JGroupsAddressCache.getPersistentUUID(members.get(i)).toString());
      }
      state.setProperty(STATE_PRIMARY_OWNERS_COUNT, Integer.toString(primaryOwners.length));
      for (int i = 0; i < primaryOwners.length; i++) {
         state.setProperty(String.format(STATE_PRIMARY_OWNERS, i), Integer.toString(primaryOwners[i]));
      }
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder("ReplicatedConsistentHash{");
      sb.append("ns = ").append(segments.size());
      sb.append(", owners = (").append(members.size()).append(")[");

      int[] primaryOwned = new int[members.size()];
      for (int i = 0; i < primaryOwners.length; i++) {
         primaryOwned[primaryOwners[i]] ++;
      }

      boolean first = true;
      for (int i = 0; i < members.size(); i++) {
         Address a = members.get(i);
         if (first) {
            first = false;
         } else {
            sb.append(", ");
         }
         sb.append(a).append(": ").append(primaryOwned[i]);
      }
      sb.append("]}");
      return sb.toString();
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((hashFunction == null) ? 0 : hashFunction.hashCode());
      result = prime * result + ((members == null) ? 0 : members.hashCode());
      result = prime * result + Arrays.hashCode(primaryOwners);
      return result;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj)
         return true;
      if (obj == null)
         return false;
      if (getClass() != obj.getClass())
         return false;
      ReplicatedConsistentHash other = (ReplicatedConsistentHash) obj;
      if (hashFunction == null) {
         if (other.hashFunction != null)
            return false;
      } else if (!hashFunction.equals(other.hashFunction))
         return false;
      if (members == null) {
         if (other.members != null)
            return false;
      } else if (!members.equals(other.members))
         return false;
      if (!Arrays.equals(primaryOwners, other.primaryOwners))
         return false;
      return true;
   }

   public static class RangeSet implements Set<Integer> {
      final int size;

      public RangeSet(int size) {
         this.size = size;
      }

      @Override
      public int size() {
         return size;
      }

      @Override
      public boolean isEmpty() {
         return size <= 0;
      }

      @Override
      public boolean contains(Object o) {
         if (!(o instanceof Integer))
            return false;
         int i = (int) o;
         return 0 <= i && i < size;
      }

      @Override
      public Iterator<Integer> iterator() {
         return new RangeSetIterator(size);
      }

      @Override
      public Object[] toArray() {
         Object[] array = new Object[size];
         for (int i = 0; i < size; i++) {
            array[i] = i;
         }
         return array;
      }

      @Override
      public <T> T[] toArray(T[] a) {
         T[] array = a.length >= size ? a :
               (T[])java.lang.reflect.Array
                     .newInstance(a.getClass().getComponentType(), size);
         for (int i = 0; i < size; i++) {
            array[i] = (T) Integer.valueOf(i);
         }
         return array;
      }

      @Override
      public boolean add(Integer integer) {
         throw new UnsupportedOperationException("RangeSet is immutable");
      }

      @Override
      public boolean remove(Object o) {
         throw new UnsupportedOperationException("RangeSet is immutable");
      }

      @Override
      public boolean containsAll(Collection<?> c) {
         for (Object o : c) {
            if (!contains(o))
               return false;
         }
         return true;
      }

      @Override
      public boolean addAll(Collection<? extends Integer> c) {
         throw new UnsupportedOperationException("RangeSet is immutable");
      }

      @Override
      public boolean retainAll(Collection<?> c) {
         throw new UnsupportedOperationException("RangeSet is immutable");
      }

      @Override
      public boolean removeAll(Collection<?> c) {
         throw new UnsupportedOperationException("RangeSet is immutable");
      }

      @Override
      public void clear() {
         throw new UnsupportedOperationException("RangeSet is immutable");
      }

      @Override
      public String toString() {
         return "RangeSet(" + size + ")";
      }

      private static class RangeSetIterator implements Iterator<Integer> {
         private int size;
         private int next;

         public RangeSetIterator(int size) {
            this.size = size;
            this.next = 0;
         }

         @Override
         public boolean hasNext() {
            return next < size;
         }

         @Override
         public Integer next() {
            return next++;
         }

         @Override
         public void remove() {
            throw new UnsupportedOperationException("RangeSet is read-only");
         }
      }
   }


   public static class Externalizer extends InstanceReusingAdvancedExternalizer<ReplicatedConsistentHash> {

      @Override
      public void doWriteObject(ObjectOutput output, ReplicatedConsistentHash ch) throws IOException {
         output.writeObject(ch.hashFunction);
         output.writeObject(ch.members);
         output.writeObject(ch.primaryOwners);
      }

      @Override
      @SuppressWarnings("unchecked")
      public ReplicatedConsistentHash doReadObject(ObjectInput unmarshaller) throws IOException, ClassNotFoundException {
         Hash hashFunction = (Hash) unmarshaller.readObject();
         List<Address> members = (List<Address>) unmarshaller.readObject();
         int[] primaryOwners = (int[]) unmarshaller.readObject();
         return new ReplicatedConsistentHash(hashFunction, members, primaryOwners);
      }

      @Override
      public Integer getId() {
         return Ids.REPLICATED_CONSISTENT_HASH;
      }

      @Override
      public Set<Class<? extends ReplicatedConsistentHash>> getTypeClasses() {
         return Collections.<Class<? extends ReplicatedConsistentHash>>singleton(ReplicatedConsistentHash.class);
      }
   }
}
