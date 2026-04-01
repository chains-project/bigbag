package github.chains.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal .env loader that reads key=value pairs and exposes helper accessors.
 */
public final class EnvConfig {

    private static final Logger log = LoggerFactory.getLogger(EnvConfig.class);
    private static final String DEFAULT_ENV_FILE = ".env";

    private final Map<String, String> values;

    private EnvConfig(Map<String, String> values) {
        this.values = values;
    }

    public static EnvConfig loadDefault() {
        return load(Paths.get(DEFAULT_ENV_FILE));
    }

    public static EnvConfig load(Path envFile) {
        if (envFile == null) {
            throw new IllegalArgumentException("envFile path cannot be null");
        }
        if (!Files.exists(envFile)) {
            throw new IllegalStateException("Missing .env file at " + envFile.toAbsolutePath()
                    + ". Please copy .env.example and adjust the values.");
        }

        Map<String, String> map = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(envFile)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int idx = trimmed.indexOf('=');
                if (idx <= 0) {
                    log.warn("Ignoring malformed .env line: {}", trimmed);
                    continue;
                }
                String key = trimmed.substring(0, idx).trim().toUpperCase(Locale.ROOT);
                String value = trimmed.substring(idx + 1).trim();
                map.put(key, value);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read .env file at " + envFile.toAbsolutePath(), e);
        }

        return new EnvConfig(Collections.unmodifiableMap(map));
    }

    public Optional<String> get(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(key.toUpperCase(Locale.ROOT)));
    }

    public Optional<Boolean> getBoolean(String key) {
        return get(key).map(value -> {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if ("true".equals(normalized) || "1".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized) || "0".equals(normalized)) {
                return false;
            }
            throw new IllegalStateException("Invalid boolean value for " + key + ": " + value);
        });
    }

    public Optional<Path> getPath(String key) {
        return get(key).map(Paths::get);
    }

    /**
     * Gets a list of paths from a comma or colon-separated string.
     * Useful for classpath configuration.
     *
     * @param key the configuration key
     * @return list of paths, or empty list if key is not found
     */
    public List<Path> getPathList(String key) {
        return get(key)
                .filter(value -> !value.isBlank())
                .map(value -> {
                    // Support both comma and colon separators (classpath standard)
                    String[] parts = value.split("[,:]");
                    List<Path> paths = new java.util.ArrayList<>();
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) {
                            try {
                                paths.add(Paths.get(trimmed));
                            } catch (Exception e) {
                                log.warn("Invalid path in {}: {}", key, trimmed);
                            }
                        }
                    }
                    return paths;
                })
                .orElse(java.util.Collections.emptyList());
    }

    public String require(String key) {
        return get(key).filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "Missing required configuration key '" + key + "' in .env or CLI arguments."));
    }
}

