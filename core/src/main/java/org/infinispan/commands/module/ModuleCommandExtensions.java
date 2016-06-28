package org.infinispan.commands.module;

/**
 * Module command extensions
 *
 * @author Galder Zamarreño
 * @since 5.1
 */
public interface ModuleCommandExtensions {

   /**
    * @deprecated Since 8.0, please implement {@link #getBasicModuleCommandFactory()} instead.
    *
    * This will be replaced with
    * "ModuleCommandFactory getModuleCommandFactory()"
    * changing the return type, as ExtendedModuleCommandFactory will be removed:
    * Please upgrade your code to use ModuleCommandFactory already.
    * NOTE: this change will break binary compatibility - don't do it as Hibernate ORM
    * is compiled using Infinispan 7 at the moment.
    */
   @Deprecated
   default ExtendedModuleCommandFactory getModuleCommandFactory() {
      throw new UnsupportedOperationException();
   }

   default ModuleCommandFactory getBasicModuleCommandFactory() {
      return getModuleCommandFactory();
   }

   ModuleCommandInitializer getModuleCommandInitializer();

}
