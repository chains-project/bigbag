package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ComprehensiveMigration {
    
    private static final Map<String, String> MIGRATION_PATTERNS = new HashMap<>();
    
    static {
        // Dropwizard 4.0.0 package migrations
        MIGRATION_PATTERNS.put("import io.dropwizard.Application;", "import io.dropwizard.core.Application;");
        MIGRATION_PATTERNS.put("import io.dropwizard.Configuration;", "import io.dropwizard.core.Configuration;");
        MIGRATION_PATTERNS.put("import io.dropwizard.ConfiguredBundle;", "import io.dropwizard.core.ConfiguredBundle;");
        
        MIGRATION_PATTERNS.put("import io.dropwizard.setup.Bootstrap;", "import io.dropwizard.core.setup.Bootstrap;");
        MIGRATION_PATTERNS.put("import io.dropwizard.setup.Environment;", "import io.dropwizard.core.setup.Environment;");
        MIGRATION_PATTERNS.put("import io.dropwizard.setup.AdminFactory;", "import io.dropwizard.core.setup.AdminFactory;");
        MIGRATION_PATTERNS.put("import io.dropwizard.setup.AdminEnvironment;", "import io.dropwizard.core.setup.AdminEnvironment;");
        
        MIGRATION_PATTERNS.put("import io.dropwizard.logging.AbstractAppenderFactory;", "import io.dropwizard.logging.common.AbstractAppenderFactory;");
        MIGRATION_PATTERNS.put("import io.dropwizard.logging.LoggingFactory;", "import io.dropwizard.logging.common.LoggingFactory;");
        MIGRATION_PATTERNS.put("import io.dropwizard.logging.filter.FilterFactory;", "import io.dropwizard.logging.common.filter.FilterFactory;");
        MIGRATION_PATTERNS.put("import io.dropwizard.logging.filter.LevelFilterFactory;", "import io.dropwizard.logging.common.filter.LevelFilterFactory;");
        MIGRATION_PATTERNS.put("import io.dropwizard.logging.layout.LayoutFactory;", "import io.dropwizard.logging.common.layout.LayoutFactory;");
        MIGRATION_PATTERNS.put("import io.dropwizard.logging.async.AsyncAppenderFactory;", "import io.dropwizard.logging.common.async.AsyncAppenderFactory;");
        
        // Type references in code
        MIGRATION_PATTERNS.put("io.dropwizard.Application", "io.dropwizard.core.Application");
        MIGRATION_PATTERNS.put("io.dropwizard.Configuration", "io.dropwizard.core.Configuration");
        MIGRATION_PATTERNS.put("io.dropwizard.ConfiguredBundle", "io.dropwizard.core.ConfiguredBundle");
        MIGRATION_PATTERNS.put("io.dropwizard.setup.Bootstrap", "io.dropwizard.core.setup.Bootstrap");
        MIGRATION_PATTERNS.put("io.dropwizard.setup.Environment", "io.dropwizard.core.setup.Environment");
        MIGRATION_PATTERNS.put("io.dropwizard.logging.AbstractAppenderFactory", "io.dropwizard.logging.common.AbstractAppenderFactory");
        MIGRATION_PATTERNS.put("io.dropwizard.logging.filter.FilterFactory", "io.dropwizard.logging.common.filter.FilterFactory");
        MIGRATION_PATTERNS.put("io.dropwizard.logging.filter.LevelFilterFactory", "io.dropwizard.logging.common.filter.LevelFilterFactory");
        MIGRATION_PATTERNS.put("io.dropwizard.logging.layout.LayoutFactory", "io.dropwizard.logging.common.layout.LayoutFactory");
        MIGRATION_PATTERNS.put("io.dropwizard.logging.async.AsyncAppenderFactory", "io.dropwizard.logging.common.async.AsyncAppenderFactory");
        
        // JAX-RS to Jakarta migrations (Dropwizard 4.0.0 uses Jakarta EE 9+)
        MIGRATION_PATTERNS.put("javax.ws.rs", "jakarta.ws.rs");
        MIGRATION_PATTERNS.put("javax.annotation", "jakarta.annotation");
    }
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java ComprehensiveMigration <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying comprehensive Dropwizard 4.0.0 migration to: " + sourceDir);
        
        final int[] totalChanges = {0};
        
        Files.walk(Paths.get(sourceDir))
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(file -> {
                try {
                    if (processFile(file)) {
                        System.out.println("Updated: " + file);
                        totalChanges[0]++;
                    }
                } catch (IOException e) {
                    System.err.println("Error processing file: " + file + " - " + e.getMessage());
                }
            });
        
        System.out.println("Total files processed with changes: " + totalChanges[0]);
    }
    
    private static boolean processFile(Path file) throws IOException {
        String content = Files.readString(file);
        String originalContent = content;
        
        // Apply all migration patterns
        for (Map.Entry<String, String> migration : MIGRATION_PATTERNS.entrySet()) {
            content = content.replace(migration.getKey(), migration.getValue());
        }
        
        // Check if content changed
        if (!content.equals(originalContent)) {
            Files.writeString(file, content);
            return true;
        }
        
        return false;
    }
}