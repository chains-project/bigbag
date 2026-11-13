package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    private static final List<PackageMigration> PACKAGE_MIGRATIONS = List.of(
        new PackageMigration("javax.ws.", "jakarta.ws."),
        new PackageMigration("javax.servlet.", "jakarta.servlet."),
        new PackageMigration("javax.validation.", "jakarta.validation.")
    );
    
    private static class PackageMigration {
        final String oldPrefix;
        final String newPrefix;
        
        PackageMigration(String oldPrefix, String newPrefix) {
            this.oldPrefix = oldPrefix;
            this.newPrefix = newPrefix;
        }
        
        boolean appliesTo(String importName) {
            return importName.startsWith(oldPrefix);
        }
        
        String migrate(String importName) {
            return importName.replace(oldPrefix, newPrefix);
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Scanning Java files in: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(sourcePath);
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            int totalChanges = 0;
            
            for (Path javaFile : javaFiles) {
                try {
                    JavaParser javaParser = new JavaParser();
                    ParseResult<CompilationUnit> parseResult = javaParser.parse(javaFile.toFile());
                    
                    if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                        CompilationUnit cu = parseResult.getResult().get();
                        LexicalPreservingPrinter.setup(cu);
                        
                        ImportVisitor visitor = new ImportVisitor();
                        visitor.visit(cu, null);
                        
                        if (visitor.getModifiedCount() > 0) {
                            String updatedContent = LexicalPreservingPrinter.print(cu);
                            Files.write(javaFile, updatedContent.getBytes());
                            modifiedFiles++;
                            totalChanges += visitor.getModifiedCount();
                            System.out.println("Modified " + javaFile + " (" + visitor.getModifiedCount() + " imports)");
                        }
                    } else {
                        System.err.println("Failed to parse: " + javaFile);
                    }
                } catch (FileNotFoundException e) {
                    System.err.println("Error reading file: " + javaFile);
                } catch (IOException e) {
                    System.err.println("Error writing file: " + javaFile);
                }
            }
            
            System.out.println("\nSummary:");
            System.out.println("Modified " + modifiedFiles + " files");
            System.out.println("Made " + totalChanges + " import replacements");
            
        } catch (IOException e) {
            System.err.println("Error scanning directory: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static List<Path> findJavaFiles(Path startDir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walkFileTree(startDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return javaFiles;
    }
    
    private static class ImportVisitor extends VoidVisitorAdapter<Void> {
        private int modifiedCount = 0;
        
        public int getModifiedCount() {
            return modifiedCount;
        }
        
        @Override
        public void visit(ImportDeclaration importDecl, Void arg) {
            super.visit(importDecl, arg);
            
            String importName = importDecl.getNameAsString();
            
            for (PackageMigration migration : PACKAGE_MIGRATIONS) {
                if (migration.appliesTo(importName)) {
                    String newImportName = migration.migrate(importName);
                    importDecl.setName(newImportName);
                    modifiedCount++;
                    break;
                }
            }
        }
    }
}