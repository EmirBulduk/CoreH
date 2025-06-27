package org.arch.me;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

/**
 * Plugin Loader for EnhancedCoreH
 * This allows loading dependencies and libraries before the plugin starts
 */
public class EnhancedCoreHLoader implements PluginLoader {

    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        // Create Maven library resolver
        MavenLibraryResolver resolver = new MavenLibraryResolver();

        // Add repositories
        resolver.addRepository(new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/").build());
        resolver.addRepository(new RemoteRepository.Builder("papermc", "default", "https://repo.papermc.io/repository/maven-public/").build());

        // Add dependencies that your plugin needs
        // HikariCP for database connection pooling
        resolver.addDependency(new Dependency(new DefaultArtifact("com.zaxxer:HikariCP:5.1.0"), null));

        // MySQL Connector
        resolver.addDependency(new Dependency(new DefaultArtifact("mysql:mysql-connector-java:8.0.33"), null));

        // SLF4J for logging (used by HikariCP)
        resolver.addDependency(new Dependency(new DefaultArtifact("org.slf4j:slf4j-api:2.0.9"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.slf4j:slf4j-simple:2.0.9"), null));

        // JSON library for metadata serialization
        resolver.addDependency(new Dependency(new DefaultArtifact("com.google.code.gson:gson:2.10.1"), null));

        // Add the resolver to the classpath builder
        classpathBuilder.addLibrary(resolver);
    }
}