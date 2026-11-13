package github.chains;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic transformation rule to fix the ByteBuddy package relocation issue.
 * 
 * This rule transforms code that uses the old AssertJ internal ByteBuddy package
 * path `org.assertj.core.internal.bytebuddy` to the new standalone ByteBuddy package
 * path `net.bytebuddy`.
 */
public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        try (Stream<Path> paths = Files.walk(dir).filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java"))) {
            List<Path> javaFiles = paths.collect(Collectors.toList());
            
            for (Path file : javaFiles) {
                fixByteBuddyImports(file);
            }
        }
    }
    
    private static void fixByteBuddyImports(Path filePath) throws IOException {
        // Read the entire file content
        String content = new String(Files.readAllBytes(filePath));
        
        // Replace the problematic imports
        String newContent = content
            .replace("import org.assertj.core.internal.bytebuddy.ByteBuddy;", "import net.bytebuddy.ByteBuddy;")
            .replace("import org.assertj.core.internal.bytebuddy.TypeCache;", "import net.bytebuddy.TypeCache;")
            .replace("import org.assertj.core.internal.bytebuddy.TypeCache.SimpleKey;", "import net.bytebuddy.TypeCache.SimpleKey;")
            .replace("import org.assertj.core.internal.bytebuddy.dynamic.scaffold.TypeValidation;", "import net.bytebuddy.dynamic.scaffold.TypeValidation;")
            .replace("import org.assertj.core.internal.bytebuddy.implementation.Implementation;", "import net.bytebuddy.implementation.Implementation;")
            .replace("import org.assertj.core.internal.bytebuddy.implementation.MethodDelegation;", "import net.bytebuddy.implementation.MethodDelegation;")
            .replace("import org.assertj.core.internal.bytebuddy.implementation.auxiliary.AuxiliaryType;", "import net.bytebuddy.implementation.auxiliary.AuxiliaryType;")
            .replace("import org.assertj.core.internal.bytebuddy.implementation.bind.annotation.RuntimeType;", "import net.bytebuddy.implementation.bind.annotation.RuntimeType;")
            .replace("import org.assertj.core.internal.bytebuddy.implementation.bind.annotation.SuperCall;", "import net.bytebuddy.implementation.bind.annotation.SuperCall;")
            .replace("import org.assertj.core.internal.bytebuddy.implementation.bind.annotation.This;", "import net.bytebuddy.implementation.bind.annotation.This;")
            .replace("import static org.assertj.core.internal.bytebuddy.matcher.ElementMatchers.any;", "import static net.bytebuddy.matcher.ElementMatchers.any;")
            .replace("org.assertj.core.internal.bytebuddy", "net.bytebuddy");
        
        // If content changed, write back to file
        if (!content.equals(newContent)) {
            BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toFile()));
            writer.write(newContent);
            writer.close();
        }
    }
}