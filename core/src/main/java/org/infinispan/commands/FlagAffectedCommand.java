package org.infinispan.commands;

import java.util.Arrays;
import java.util.Set;

import org.infinispan.commons.util.EnumUtil;
import org.infinispan.context.Flag;
import org.infinispan.context.impl.FlagBitSets;

/**
 * Flags modify behavior of command such as whether or not to invoke certain commands remotely, check cache store etc.
 *
 * @author William Burns
 * @author Sanne Grinovero
 * @since 5.0
 */
public interface FlagAffectedCommand extends VisitableCommand {
   /**
    * @return The command flags - only valid to invoke after {@link #setFlags(java.util.Set)}. The set should
    * not be modified directly, only via the {@link #setFlags(Set)}, {@link #addFlag(Flag)} and {@link
    * #addFlags(Set)} methods.
    */
   default Set<Flag> getFlags() {
      return EnumUtil.enumSetOf(getFlagsBitSet(), Flag.class);
   }

   long getFlagsBitSet();

   /**
    * Set the flags, replacing any existing flags.
    *
    * @param flags The new flags.
    */
   default void setFlags(Set<Flag> flags) {
      setFlagsBitSet(EnumUtil.bitSetOf(flags));
   }

   void setFlagsBitSet(long bitSet);

   /**
    * Add some flags to the command.
    *
    * @deprecated Use either {@link #addFlag(Flag)} or {@link #addFlags(Set)} instead.
    *
    * @param newFlags The flags to add.
    */
   @Deprecated
   default void setFlags(Flag... newFlags) {
      setFlagsBitSet(EnumUtil.setEnums(getFlagsBitSet(), Arrays.asList(newFlags)));
   }

   /**
    * Add a single flag to the command.
    *
    * @param flag The flag to add.
    */
   default void addFlag(Flag flag) {
      setFlagsBitSet(EnumUtil.setEnum(getFlagsBitSet(), flag));
   }

   /**
    * Add a set of flags to the command.
    *
    * @param flags The flags to add.
    */
   default void addFlags(Set<Flag> flags) {
      setFlagsBitSet(EnumUtil.setEnums(getFlagsBitSet(), flags));
   }

   /**
    * Check whether a particular flag is present in the command
    *
    * @param flag to lookup in the command
    * @return true if the flag is present
    */
   default boolean hasFlag(Flag flag) {
      return EnumUtil.hasEnum(getFlagsBitSet(), flag);
   }

   /**
    * Check whether any of the flags in the {@code testBitSet} parameter is present in the command.
    *
    * Should be used with the constants in {@link FlagBitSets}
    */
   default boolean hasAnyFlag(long testBitSet) {
      return EnumUtil.containsAny(getFlagsBitSet(), testBitSet);
   }
}
