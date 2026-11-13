package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    public static class LogbackMigrationVisitor extends VoidVisitorAdapter<Void> {
        private final List<String> issues = new ArrayList<>();
        private boolean usesLogbackLogger = false;
        
        @Override
        public void visit(ImportDeclaration id, Void arg) {
            super.visit(id, arg);
            String importName = id.getNameAsString();
            if (importName.equals("ch.qos.logback.classic.Logger")) {
                usesLogbackLogger = true;
                issues.add("File imports ch.qos.logback.classic.Logger which requires SLF4J 2.x when used with logback-classic 1.4.6+");
            }
        }
        
        @Override
        public void visit(CastExpr cast, Void arg) {
            super.visit(cast, arg);
            if (cast.getType().isClassOrInterfaceType()) {
                ClassOrInterfaceType type = cast.getType().asClassOrInterfaceType();
                if (type.getNameAsString().equals("Logger")) {
                    issues.add("Line " + cast.getBegin().map(p -> p.line).orElse(0) + 
                              ": Cast to Logger detected. This may be ch.qos.logback.classic.Logger which requires SLF4J 2.x when used with logback-classic 1.4.6+");
                }
            }
        }
        
        public List<String> getIssues() {
            return issues;
        }
        
        public boolean usesLogbackLogger() {
            return usesLogbackLogger;
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source-directory>");
            System.err.println("Example: java -cp target/classes github.chains.Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        System.out.println("Analyzing source directory: " + sourceDir);
        System.out.println("Looking for compatibility issues between logback-classic 1.4.6+ and SLF4J 1.x");
        System.out.println("==================================================================");
        
        JavaParser parser = new JavaParser();
        List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
        int totalIssues = 0;
        int filesWithIssues = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                if (cu != null) {
                    LogbackMigrationVisitor visitor = new LogbackMigrationVisitor();
                    visitor.visit(cu, null);
                    
                    if (!visitor.getIssues().isEmpty()) {
                        filesWithIssues++;
                        System.out.println("\nFile: " + javaFile);
                        for (String issue : visitor.getIssues()) {
                            System.out.println("  - " + issue);
                            totalIssues++;
                        }
                        
                        if (visitor.usesLogbackLogger()) {
                            System.out.println("\n  Recommended fixes:");
                            System.out.println("  1. Upgrade SLF4J to version 2.0.4 or later in your pom.xml/build.gradle");
                            System.out.println("     <dependency>");
                            System.out.println("       <groupId>org.slf4j</groupId>");
                            System.out.println("       <artifactId>slf4j-api</artifactId>");
                            System.out.println("       <version>2.0.4</version>");
                            System.out.println("     </dependency>");
                            System.out.println("  2. Or downgrade logback-classic to a version compatible with SLF4J 1.x");
                            System.out.println("     <dependency>");
                            System.out.println("       <groupId>ch.qos.logback</groupId>");
                            System.out.println("       <artifactId>logback-classic</artifactId>");
                            System.out.println("       <version>1.2.11</version> <!-- Compatible with SLF4J 1.7.x -->");
                            System.out.println("     </dependency>");
                            System.out.println("  3. Or refactor code to avoid direct use of ch.qos.logback.classic.Logger");
                            System.out.println("     (use org.slf4j.Logger interface instead)");
                            
                            System.out.println("\n  Generic transformation pattern:");
                            System.out.println("  - Find: Import of 'ch.qos.logback.classic.Logger'");
                            System.out.println("  - Find: Cast expressions to 'Logger' type");
                            System.out.println("  - Apply: Update dependencies or refactor code");
                        }
                    }
                }
            } catch (FileNotFoundException e) {
                System.err.println("Error reading file: " + javaFile + " - " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Error parsing file: " + javaFile + " - " + e.getMessage());
            }
        }
        
        System.out.println("\n==================================================================");
        System.out.println("Analysis complete.");
        System.out.println("Files with issues: " + filesWithIssues);
        System.out.println("Total issues found: " + totalIssues);
        
        if (totalIssues > 0) {
            System.out.println("\nBackground:");
            System.out.println("logback-classic 1.4.6 requires SLF4J 2.0.4 or later.");
            System.out.println("The ch.qos.logback.classic.Logger class implements");
            System.out.println("org.slf4j.spi.LoggingEventAware which was introduced in SLF4J 2.x.");
            System.out.println("Using logback-classic 1.4.6+ with SLF4J 1.x will cause");
            System.out.println("\"cannot access org.slf4j.spi.LoggingEventAware\" compilation errors.");
            
            System.out.println("\nGeneric transformation rule summary:");
            System.out.println("1. Pattern: Import of 'ch.qos.logback.classic.Logger' or cast to 'Logger'");
            System.out.println("2. Required transformation: Update SLF4J to 2.0.4+ in build configuration");
            System.out.println("3. Alternative: Downgrade logback-classic to 1.2.x (compatible with SLF4J 1.x)");
            System.out.println("4. Applicable to any project using logback-classic 1.4.6+ with SLF4J 1.x");
        }
    }
    
    private static List<Path> findJavaFiles(Path directory) {
        List<Path> javaFiles = new ArrayList<>();
        findJavaFilesRecursive(directory, javaFiles);
        return javaFiles;
    }
    
    private static void findJavaFilesRecursive(Path directory, List<Path> javaFiles) {
        File dir = directory.toFile();
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            if (file.isDirectory()) {
                findJavaFilesRecursive(file.toPath(), javaFiles);
            } else if (file.getName().endsWith(".java")) {
                javaFiles.add(file.toPath());
            }
        }
    }
}