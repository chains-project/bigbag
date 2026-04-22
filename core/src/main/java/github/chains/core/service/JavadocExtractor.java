package github.chains.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import github.chains.core.model.BreakingUpdateRecord;
import github.chains.core.model.UpdatedDependency;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Extracts the Javadoc for the new version of a dependency into the extracted
 * project directory so that all analysis agents can reference it.
 *
 * <p>Javadoc JAR resolution order:
 * <ol>
 *   <li>{@code JAVADOC_CACHE_DIR} (shared cache, avoids re-downloading across runs)</li>
 *   <li>{@code extractedPath/} root (copied from the breaking Docker image)</li>
 *   <li>{@code extractedPath/m2/.m2/repository/...}</li>
 *   <li>{@code ~/.m2/repository/...}</li>
 *   <li>Maven Central — downloaded and saved to cache if available</li>
 * </ol>
 *
 * <p>Extracted HTML is placed at:
 * {@code extractedPath/javadoc-{artifactId}-{newVersion}/}
 */
public class JavadocExtractor {

    private static final Logger log = LoggerFactory.getLogger(JavadocExtractor.class);

    private static final String MAVEN_CENTRAL_BASE = "https://repo1.maven.org/maven2";
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(2);

    private final Path cacheDir;

    public JavadocExtractor() {
        this.cacheDir = null;
    }

    public JavadocExtractor(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    /**
     * Resolves the Javadoc JAR for the new dependency version and extracts it
     * alongside the project.
     *
     * @param record        the breaking update record (provides dependency coordinates)
     * @param extractedPath the directory where the project and JARs are extracted
     * @return {@code true} if Javadoc was successfully extracted, {@code false} otherwise
     */
    public boolean extract(BreakingUpdateRecord record, Path extractedPath) {
        if (record == null || record.updatedDependency() == null || extractedPath == null) {
            log.error("JavadocExtractor: missing record or extractedPath");
            return false;
        }

        UpdatedDependency dep = record.updatedDependency();
        String groupId    = dep.dependencyGroupId();
        String artifactId = dep.dependencyArtifactId();
        String version    = dep.newVersion();

        if (isBlank(groupId) || isBlank(artifactId) || isBlank(version)) {
            log.error("JavadocExtractor: incomplete dependency coordinates");
            return false;
        }

        Path javadocJar = resolveJavadocJar(groupId, artifactId, version, extractedPath);
        if (javadocJar == null) {
            log.error("JavadocExtractor: Javadoc JAR not found locally or on Maven Central for {}:{} v{}",
                    groupId, artifactId, version);
            return false;
        }

        Path javadocDir = extractedPath.resolve("javadoc-" + artifactId + "-" + version);
        log.info("JavadocExtractor: extracting {} → {}", javadocJar.getFileName(), javadocDir);

        try {
            Files.createDirectories(javadocDir);
            try (JarFile jar = new JarFile(javadocJar.toFile())) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    Path target = javadocDir.resolve(entry.getName()).normalize();

                    // Prevent zip-slip
                    if (!target.startsWith(javadocDir)) {
                        log.warn("JavadocExtractor: skipping unsafe entry {}", entry.getName());
                        continue;
                    }

                    if (entry.isDirectory()) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        try (InputStream in = jar.getInputStream(entry)) {
                            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
            log.info("JavadocExtractor: Javadoc extracted to {}", javadocDir);
            return true;
        } catch (IOException e) {
            log.error("JavadocExtractor: failed to extract Javadoc JAR {}: {}", javadocJar, e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // JAR resolution
    // -------------------------------------------------------------------------

    private Path resolveJavadocJar(String groupId, String artifactId, String version, Path extractedPath) {
        String javadocJarName = artifactId + "-" + version + "-javadoc.jar";
        String groupPath      = groupId.replace('.', '/');

        // 1. Shared cache (JAVADOC_CACHE_DIR)
        if (cacheDir != null) {
            Path cached = cacheDir.resolve(javadocJarName);
            if (Files.isRegularFile(cached)) {
                log.info("JavadocExtractor: found Javadoc JAR in cache: {}", cached);
                return cached;
            }
        }

        // 2. Directly in extractedPath root (copied from the breaking Docker image)
        Path direct = extractedPath.resolve(javadocJarName);
        if (Files.isRegularFile(direct)) {
            log.debug("JavadocExtractor: found Javadoc JAR at extracted root: {}", direct);
            return direct;
        }

        // 3. In the project's embedded m2 cache
        Path fromProjectM2 = extractedPath
                .resolve("m2").resolve(".m2").resolve("repository")
                .resolve(groupPath).resolve(artifactId).resolve(version).resolve(javadocJarName);
        if (Files.isRegularFile(fromProjectM2)) {
            log.debug("JavadocExtractor: found Javadoc JAR in project m2: {}", fromProjectM2);
            return fromProjectM2;
        }

        // 4. User's local Maven repository
        Path userM2 = Paths.get(System.getProperty("user.home"), ".m2", "repository")
                .resolve(groupPath).resolve(artifactId).resolve(version).resolve(javadocJarName);
        if (Files.isRegularFile(userM2)) {
            log.debug("JavadocExtractor: found Javadoc JAR in ~/.m2: {}", userM2);
            return userM2;
        }

        // 5. Download from Maven Central — save to cache if available, else to extractedPath
        String mavenCentralUrl = MAVEN_CENTRAL_BASE + "/" + groupPath + "/" + artifactId
                + "/" + version + "/" + javadocJarName;
        Path downloadDest = cacheDir != null ? cacheDir.resolve(javadocJarName)
                                             : extractedPath.resolve(javadocJarName);
        if (cacheDir != null) {
            try {
                Files.createDirectories(cacheDir);
            } catch (IOException e) {
                log.warn("JavadocExtractor: could not create cache dir {}: {}", cacheDir, e.getMessage());
                downloadDest = extractedPath.resolve(javadocJarName);
            }
        }
        return downloadFromMavenCentral(mavenCentralUrl, downloadDest);
    }

    /**
     * Downloads the Javadoc JAR from Maven Central into {@code dest}.
     * Returns {@code dest} on success, {@code null} if the artifact is not
     * found (404) or any network/IO error occurs.
     */
    private Path downloadFromMavenCentral(String url, Path dest) {
        log.info("JavadocExtractor: downloading Javadoc JAR from Maven Central: {}", url);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(DOWNLOAD_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(DOWNLOAD_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<InputStream> response = client.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 404) {
                log.warn("JavadocExtractor: Javadoc JAR not published on Maven Central (404): {}", url);
                return null;
            }
            if (response.statusCode() != 200) {
                log.warn("JavadocExtractor: unexpected HTTP {} when downloading {}", response.statusCode(), url);
                return null;
            }

            try (InputStream body = response.body()) {
                Files.copy(body, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("JavadocExtractor: downloaded Javadoc JAR ({} bytes) → {}", Files.size(dest), dest);
            return dest;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("JavadocExtractor: download interrupted: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("JavadocExtractor: failed to download Javadoc JAR from {}: {}", url, e.getMessage());
            return null;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
