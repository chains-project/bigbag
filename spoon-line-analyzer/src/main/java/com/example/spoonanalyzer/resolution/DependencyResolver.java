package com.example.spoonanalyzer.resolution;

import com.example.spoonanalyzer.model.DependencyInfo;
import com.example.spoonanalyzer.model.DependencyOrigin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Enumeration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class DependencyResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyResolver.class);

    private final Path projectRoot;
    private final ClassLoader modelClassLoader;
    private final Map<String, DependencyInfo> typeCache = new ConcurrentHashMap<>();
    private final Map<Path, DependencyInfo> jarCache = new ConcurrentHashMap<>();

    public DependencyResolver(Path projectRoot, ClassLoader modelClassLoader) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.modelClassLoader = modelClassLoader;
    }

    public DependencyInfo resolveType(CtTypeReference<?> typeReference) {
        if (typeReference == null) {
            return DependencyInfo.builder(DependencyOrigin.UNKNOWN).build();
        }
        String qualifiedName = typeReference.getQualifiedName();
        if (qualifiedName == null || typeReference.isPrimitive()) {
            return DependencyInfo.builder(DependencyOrigin.JDK).build();
        }
        return typeCache.computeIfAbsent(qualifiedName, key -> computeTypeDependency(typeReference));
    }

    public DependencyInfo resolveExecutable(CtExecutableReference<?> executableReference) {
        if (executableReference == null) {
            return DependencyInfo.builder(DependencyOrigin.UNKNOWN).build();
        }
        CtTypeReference<?> declaringType = executableReference.getDeclaringType();
        return resolveType(declaringType);
    }

    public DependencyInfo resolveField(CtFieldReference<?> fieldReference) {
        if (fieldReference == null) {
            return DependencyInfo.builder(DependencyOrigin.UNKNOWN).build();
        }
        CtTypeReference<?> declaringType = fieldReference.getDeclaringType();
        return resolveType(declaringType);
    }

    private DependencyInfo computeTypeDependency(CtTypeReference<?> typeReference) {
        if (typeReference.getTypeDeclaration() != null
                && typeReference.getTypeDeclaration().getPosition() != null
                && typeReference.getTypeDeclaration().getPosition().isValidPosition()) {
            Path sourcePath = typeReference.getTypeDeclaration().getPosition().getFile().toPath().toAbsolutePath();
            if (sourcePath.startsWith(projectRoot)) {
                return DependencyInfo.builder(DependencyOrigin.PROJECT_SOURCE)
                        .sourcePath(projectRoot.relativize(sourcePath))
                        .build();
            }
        }

        Class<?> actualClass = tryGetActualClass(typeReference);
        if (actualClass == null) {
            return DependencyInfo.builder(DependencyOrigin.UNKNOWN).build();
        }

        return resolveFromClass(actualClass);
    }

    private Class<?> tryGetActualClass(CtTypeReference<?> typeReference) {
        try {
            Class<?> actualClass = typeReference.getActualClass();
            if (actualClass != null) {
                return actualClass;
            }
        } catch (Throwable throwable) {
            LOGGER.debug("Could not load class via CtTypeReference#getActualClass for {}: {}",
                    typeReference.getQualifiedName(), throwable.getMessage());
        }

        if (modelClassLoader != null) {
            try {
                return Class.forName(typeReference.getQualifiedName(), false, modelClassLoader);
            } catch (ClassNotFoundException ex) {
                LOGGER.debug("Class not found in model class loader for {}", typeReference.getQualifiedName());
            } catch (LinkageError linkageError) {
                LOGGER.debug("Linkage error while loading {}: {}", typeReference.getQualifiedName(), linkageError.getMessage());
            } catch (Throwable throwable) {
                LOGGER.debug("Unexpected error while loading {}: {}", typeReference.getQualifiedName(), throwable.getMessage());
            }
        }
        return null;
    }

    private DependencyInfo resolveFromClass(Class<?> clazz) {
        ProtectionDomain protectionDomain = clazz.getProtectionDomain();
        if (protectionDomain == null) {
            return DependencyInfo.builder(DependencyOrigin.UNKNOWN).build();
        }
        CodeSource codeSource = protectionDomain.getCodeSource();
        if (codeSource == null) {
            if (clazz.getModule() != null && clazz.getModule().isNamed()) {
                return DependencyInfo.builder(DependencyOrigin.JDK)
                        .groupId("java")
                        .artifactId(clazz.getModule().getName())
                        .build();
            }
            return DependencyInfo.builder(DependencyOrigin.UNKNOWN).build();
        }

        URL location = codeSource.getLocation();
        if (location == null) {
            return DependencyInfo.builder(DependencyOrigin.UNKNOWN).build();
        }

        if ("jrt".equalsIgnoreCase(location.getProtocol())) {
            String moduleName = clazz.getModule() != null ? clazz.getModule().getName() : "java.base";
            return DependencyInfo.builder(DependencyOrigin.JDK)
                    .groupId("java")
                    .artifactId(moduleName)
                    .build();
        }

        Path path = toPath(location).orElse(null);
        if (path == null) {
            return DependencyInfo.builder(DependencyOrigin.UNKNOWN).build();
        }

        if (Files.isDirectory(path)) {
            Path normalized = path.toAbsolutePath().normalize();
            if (normalized.startsWith(projectRoot)) {
                return DependencyInfo.builder(DependencyOrigin.PROJECT_SOURCE)
                        .sourcePath(projectRoot.relativize(normalized))
                        .build();
            }
            return DependencyInfo.builder(DependencyOrigin.UNKNOWN)
                    .sourcePath(normalized)
                    .build();
        }

        return jarCache.computeIfAbsent(path.toAbsolutePath().normalize(), this::resolveJarDependency);
    }

    private Optional<Path> toPath(URL location) {
        try {
            URI uri = location.toURI();
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                return Optional.of(Paths.get(uri));
            }
            return Optional.empty();
        } catch (URISyntaxException e) {
            LOGGER.warn("Unable to convert location {} to URI: {}", location, e.getMessage());
            return Optional.empty();
        }
    }

    private DependencyInfo resolveJarDependency(Path jarPath) {
        try {
            MavenCoordinate coordinate = readCoordinate(jarPath).orElse(null);
            if (coordinate != null) {
                return DependencyInfo.builder(DependencyOrigin.MAVEN_DEPENDENCY)
                        .groupId(coordinate.groupId())
                        .artifactId(coordinate.artifactId())
                        .version(coordinate.version())
                        .sourcePath(jarPath)
                        .build();
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to inspect jar {}: {}", jarPath, e.getMessage());
        }
        return DependencyInfo.builder(DependencyOrigin.UNKNOWN)
                .sourcePath(jarPath)
                .build();
    }

    private Optional<MavenCoordinate> readCoordinate(Path jarPath) throws IOException {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().startsWith("META-INF/maven/") || !entry.getName().endsWith("pom.properties")) {
                    continue;
                }
                try (InputStream stream = jarFile.getInputStream(entry)) {
                    Properties properties = new Properties();
                    properties.load(stream);
                    String groupId = properties.getProperty("groupId");
                    String artifactId = properties.getProperty("artifactId");
                    String version = properties.getProperty("version");
                    if (groupId != null && artifactId != null) {
                        return Optional.of(new MavenCoordinate(groupId, artifactId, version));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private record MavenCoordinate(String groupId, String artifactId, String version) {
        private MavenCoordinate {
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(artifactId, "artifactId");
        }
    }
}

