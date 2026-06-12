package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            int totalFiles = javaFiles.size();
            int processedFiles = 0;
            int modifiedFiles = 0;
            
            for (Path javaFile : javaFiles) {
                processedFiles++;
                System.out.printf("Processing file %d/%d: %s%n", 
                    processedFiles, totalFiles, javaFile);
                
                if (processFile(javaFile.toFile())) {
                    modifiedFiles++;
                }
            }
            
            System.out.printf("Processed %d files, modified %d files%n", 
                processedFiles, modifiedFiles);
            
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static boolean processFile(File file) throws FileNotFoundException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(file).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse " + file)
        );
        
        final boolean[] modifiedHolder = new boolean[]{false};
        
        // Pattern 1: Update imports for class renames
        // Use a copy of imports to avoid ConcurrentModificationException
        List<ImportDeclaration> imports = new ArrayList<>(cu.getImports());
        for (ImportDeclaration importDecl : imports) {
            String importName = importDecl.getNameAsString();
            
            // Pattern 1a: LengthOf moved from iterable to scalar package
            if (importName.equals("org.cactoos.iterable.LengthOf")) {
                importDecl.setName("org.cactoos.scalar.LengthOf");
                modifiedHolder[0] = true;
                System.out.println("  Updated import: " + importName + " -> org.cactoos.scalar.LengthOf");
            }
            
            // Pattern 2: Remove "Text" suffix from text classes
            else if (importName.startsWith("org.cactoos.text.")) {
                String className = importName.substring("org.cactoos.text.".length());
                String newName = null;
                
                if (className.equals("SplitText")) {
                    newName = "org.cactoos.text.Split";
                } else if (className.equals("TrimmedText")) {
                    newName = "org.cactoos.text.Trimmed";
                } else if (className.equals("JoinedText")) {
                    newName = "org.cactoos.text.Joined";
                } else if (className.equals("RandomText")) {
                    newName = "org.cactoos.text.Randomized";
                }
                
                if (newName != null) {
                    importDecl.setName(newName);
                    modifiedHolder[0] = true;
                    System.out.println("  Updated import: " + importName + " -> " + newName);
                }
            }
            
            // Pattern 3: Remove "Scalar" suffix from scalar classes
            else if (importName.startsWith("org.cactoos.scalar.")) {
                String className = importName.substring("org.cactoos.scalar.".length());
                String newName = null;
                
                if (className.equals("CheckedScalar")) {
                    newName = "org.cactoos.scalar.Checked";
                } else if (className.equals("IoCheckedScalar")) {
                    newName = "org.cactoos.scalar.IoChecked";
                } else if (className.equals("UncheckedScalar")) {
                    newName = "org.cactoos.scalar.Unchecked";
                } else if (className.equals("SolidScalar")) {
                    newName = "org.cactoos.scalar.Solid";
                } else if (className.equals("StickyScalar")) {
                    newName = "org.cactoos.scalar.Sticky";
                }
                
                if (newName != null) {
                    importDecl.setName(newName);
                    modifiedHolder[0] = true;
                    System.out.println("  Updated import: " + importName + " -> " + newName);
                }
            }
            
            // Pattern 4: Filtered moved from collection to iterable package
            else if (importName.equals("org.cactoos.collection.Filtered")) {
                importDecl.setName("org.cactoos.iterable.Filtered");
                modifiedHolder[0] = true;
                System.out.println("  Updated import: " + importName + " -> org.cactoos.iterable.Filtered");
            }
            
            // Pattern 5: CollectionOf - will be handled by code transformation
            else if (importName.equals("org.cactoos.collection.CollectionOf")) {
                cu.getImports().remove(importDecl);
                // Add LengthOf import if not already present
                boolean hasLengthOf = cu.getImports().stream()
                    .anyMatch(imp -> imp.getNameAsString().equals("org.cactoos.scalar.LengthOf"));
                if (!hasLengthOf) {
                    cu.addImport("org.cactoos.scalar.LengthOf");
                }
                modifiedHolder[0] = true;
                System.out.println("  Removed import: " + importName + " and added LengthOf import");
            }
        }
        
        // Pattern 5: Replace CollectionOf usage with LengthOf
        cu.accept(new VoidVisitorAdapter<Void>() {
            private void updateTypeName(com.github.javaparser.ast.type.Type type, String typeName) {
                String newName = null;
                
                // Pattern 2: Remove "Text" suffix
                if (typeName.equals("SplitText")) {
                    newName = "Split";
                } else if (typeName.equals("TrimmedText")) {
                    newName = "Trimmed";
                } else if (typeName.equals("JoinedText")) {
                    newName = "Joined";
                } else if (typeName.equals("RandomText")) {
                    newName = "Randomized";
                }
                
                // Pattern 3: Remove "Scalar" suffix
                else if (typeName.equals("CheckedScalar")) {
                    newName = "Checked";
                } else if (typeName.equals("IoCheckedScalar")) {
                    newName = "IoChecked";
                } else if (typeName.equals("UncheckedScalar")) {
                    newName = "Unchecked";
                } else if (typeName.equals("SolidScalar")) {
                    newName = "Solid";
                } else if (typeName.equals("StickyScalar")) {
                    newName = "Sticky";
                }
                
                if (newName != null) {
                    if (type instanceof ClassOrInterfaceType) {
                        ((ClassOrInterfaceType) type).setName(newName);
                    } else if (type instanceof com.github.javaparser.ast.type.Type) {
                        // Try to handle other type representations
                        try {
                            // For simplicity, we'll just log and handle in specific visitors
                        } catch (Exception e) {
                            // Ignore - handled in specific visitors
                        }
                    }
                    modifiedHolder[0] = true;
                    System.out.println("  Updated type reference: " + typeName + " -> " + newName);
                }
                // Pattern 4: Filtered - type name stays the same, but import changed
                else if (typeName.equals("Filtered")) {
                    // Type name stays "Filtered" but package changed from collection to iterable
                    // This is handled by import updates above
                }
                
                // Pattern 1: LengthOf - type name stays the same, but import changed
                else if (typeName.equals("LengthOf")) {
                    // Type name stays "LengthOf" but package changed from iterable to scalar
                    // This is handled by import updates above
                }
                
                // Pattern 5: CollectionOf - should be replaced with LengthOf
                else if (typeName.equals("CollectionOf")) {
                    // This case is handled in the MethodCallExpr visitor above
                    // when we see CollectionOf(...).size()
                }
            }
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                super.visit(n, arg);
                
                // Check for pattern: new CollectionOf<>(...).size()
                if (n.getNameAsString().equals("size") && 
                    n.getScope().isPresent() && 
                    n.getScope().get() instanceof ObjectCreationExpr) {
                    
                    ObjectCreationExpr creation = (ObjectCreationExpr) n.getScope().get();
                    if (creation.getType().toString().equals("CollectionOf")) {
                        // Replace with new LengthOf(...).value()
                        ObjectCreationExpr lengthOfCreation = new ObjectCreationExpr();
                        lengthOfCreation.setType("LengthOf");
                        
                        // Copy the arguments from CollectionOf constructor
                        if (!creation.getArguments().isEmpty()) {
                            lengthOfCreation.setArguments(creation.getArguments());
                        }
                        
                        MethodCallExpr valueCall = new MethodCallExpr(lengthOfCreation, "value");
                        n.replace(valueCall);
                        modifiedHolder[0] = true;
                        System.out.println("  Replaced CollectionOf(...).size() with LengthOf(...).value()");
                    }
                }
                
                // Also handle intValue() calls on LengthOf objects
                if (n.getNameAsString().equals("intValue") && 
                    n.getScope().isPresent()) {
                    // Check if scope is a LengthOf object creation or method call
                    String scopeStr = n.getScope().get().toString();
                    if (scopeStr.contains("new LengthOf") || scopeStr.contains("LengthOf")) {
                        // Replace intValue() with value()
                        n.setName("value");
                        modifiedHolder[0] = true;
                        System.out.println("  Replaced LengthOf(...).intValue() with LengthOf(...).value()");
                    }
                }
            }
            
@Override
            public void visit(ObjectCreationExpr n, Void arg) {
                super.visit(n, arg);
                
                // Update class names in object creation expressions
                String typeName = n.getType().toString();
                updateTypeName(n.getType(), typeName);
            }
            
            @Override
            public void visit(ClassOrInterfaceType n, Void arg) {
                super.visit(n, arg);
                
                // Update class names in type references (fields, method parameters, etc.)
                String typeName = n.getNameAsString();
                updateTypeName(n, typeName);
            }
        }, null);
        
        if (modifiedHolder[0]) {
            // Write the modified file back
            try {
                Files.write(file.toPath(), cu.toString().getBytes());
                System.out.println("  File modified: " + file);
            } catch (IOException e) {
                System.err.println("  Error writing file " + file + ": " + e.getMessage());
            }
        }
        
        return modifiedHolder[0];
    }
}