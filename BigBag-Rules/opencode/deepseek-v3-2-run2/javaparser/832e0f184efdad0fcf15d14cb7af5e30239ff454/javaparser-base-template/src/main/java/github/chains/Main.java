package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        System.out.println("Processing source directory: " + sourceDirectory);

        try {
            List<File> javaFiles = Files.walk(Paths.get(sourceDirectory))
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());

            System.out.println("Found " + javaFiles.size() + " Java files");

            int totalChanges = 0;
            for (File javaFile : javaFiles) {
                int changes = processFile(javaFile);
                totalChanges += changes;
                if (changes > 0) {
                    System.out.println("Modified " + javaFile.getName() + " (" + changes + " changes)");
                }
            }

            System.out.println("Total changes applied: " + totalChanges);

        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static int processFile(File javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + javaFile.getName())
        );

        ParseEnchantmentToGetEnchantVisitor visitor = new ParseEnchantmentToGetEnchantVisitor();
        cu.accept(visitor, null);

        if (visitor.getChangeCount() > 0) {
            Files.write(javaFile.toPath(), cu.toString().getBytes());
        }

        return visitor.getChangeCount();
    }

    private static class ParseEnchantmentToGetEnchantVisitor extends ModifierVisitor<Void> {
        private int changeCount = 0;

        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Match pattern: XEnchantment.parseEnchantment()
            if (n.getNameAsString().equals("parseEnchantment") && n.getArguments().isEmpty()) {
                // Check if the scope is XEnchantment (could be direct call or through matchXEnchantment().get())
                if (isXEnchantmentCall(n)) {
                    // Replace parseEnchantment() with getEnchant()
                    n.setName("getEnchant");
                    changeCount++;
                    System.out.println("Replaced parseEnchantment() with getEnchant() at line " + n.getBegin().map(pos -> pos.line).orElse(-1));
                }
            }
            return super.visit(n, arg);
        }

        private boolean isXEnchantmentCall(MethodCallExpr n) {
            // Check if this is a call on XEnchantment instance
            // This could be:
            // - XEnchantment.matchXEnchantment("power").get().parseEnchantment()
            // - Some XEnchantment instance.parseEnchantment()
            // We want to be generic and match any call to parseEnchantment() on XEnchantment
            
            // Since parseEnchantment() was an instance method on XEnchantment,
            // any call to parseEnchantment() without arguments should be replaced
            return true;
        }

        public int getChangeCount() {
            return changeCount;
        }
    }
}