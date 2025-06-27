package org.arch.me;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import org.jetbrains.annotations.NotNull;

/**
 * Bootstrap class for EnhancedCoreH Paper plugin
 * This runs before the main plugin class and allows early initialization
 */
public class EnhancedCoreHBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        LifecycleEventManager<BootstrapContext> manager = context.getLifecycleManager();

        // Early initialization can go here
        context.getLogger().info("EnhancedCoreH Bootstrap: Initializing early systems...");

        // Register lifecycle events if needed
        // manager.registerEventHandler(LifecycleEvents.COMMANDS, this::registerCommands);

        context.getLogger().info("EnhancedCoreH Bootstrap: Early initialization complete!");
    }

    // Example lifecycle event handler
    // private void registerCommands(@NotNull LifecycleEvent event) {
    //     // Register commands early if needed
    // }
}