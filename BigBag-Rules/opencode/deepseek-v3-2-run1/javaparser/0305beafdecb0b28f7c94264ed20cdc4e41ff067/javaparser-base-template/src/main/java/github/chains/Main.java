package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming MySQL connector imports in: " + sourceDir);
        
        try {
            transformMySQLImports(new File(sourceDir));
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformMySQLImports(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        transformMySQLImports(file);
                    } else if (file.getName().endsWith(".java")) {
                        transformFile(file);
                    }
                }
            }
        }
    }
    
    private static void transformFile(File javaFile) {
        JavaParser parser = new JavaParser();
        try {
            Optional<CompilationUnit> cuOpt = parser.parse(javaFile).getResult();
            
            if (!cuOpt.isPresent()) {
                System.err.println("Failed to parse: " + javaFile.getPath());
                return;
            }
            
            CompilationUnit cu = cuOpt.get();
            boolean modified = false;
            
            List<ImportDeclaration> imports = cu.getImports();
            for (ImportDeclaration importDecl : imports) {
                String importName = importDecl.getNameAsString();
                
                // MySQL Connector/J 8.0 Package Restructuring Transformation Rules
                // Based on analysis of breaking changes from older versions to 8.0.28
                
                // Rule 1: com.mysql.jdbc.exceptions.* -> com.mysql.cj.jdbc.exceptions.*
                // This is a confirmed breaking change - exception classes moved
                if (importName.startsWith("com.mysql.jdbc.exceptions.")) {
                    String newImportName = importName.replaceFirst("com\\.mysql\\.jdbc\\.exceptions\\.", "com.mysql.cj.jdbc.exceptions.");
                    importDecl.setName(new Name(newImportName));
                    System.out.println("Transformed import (exceptions): " + importName + " -> " + newImportName);
                    modified = true;
                }
                
                // Rule 2: com.mysql.jdbc.* (generic JDBC classes) -> com.mysql.cj.jdbc.*
                // Many JDBC implementation classes moved, but some have backward compatibility shims
                // We should transform most, but be careful with Driver which has backward compatibility
                else if (importName.startsWith("com.mysql.jdbc.")) {
                    // Check for specific classes that should NOT be transformed
                    boolean shouldTransform = true;
                    
                    // Driver class has backward compatibility shim
                    if (importName.equals("com.mysql.jdbc.Driver")) {
                        shouldTransform = false;
                        System.out.println("Skipping Driver import (has backward compatibility): " + importName);
                    }
                    // SocketFactoryWrapper is another backward compatibility class
                    else if (importName.equals("com.mysql.jdbc.SocketFactoryWrapper")) {
                        shouldTransform = false;
                        System.out.println("Skipping SocketFactoryWrapper import (has backward compatibility): " + importName);
                    }
                    // jdbc2 package is legacy and might have different rules
                    else if (importName.startsWith("com.mysql.jdbc.jdbc2.")) {
                        shouldTransform = false;
                        System.out.println("Skipping jdbc2.* import (legacy package): " + importName);
                    }
                    
                    if (shouldTransform) {
                        String newImportName = importName.replaceFirst("com\\.mysql\\.jdbc\\.", "com.mysql.cj.jdbc.");
                        importDecl.setName(new Name(newImportName));
                        System.out.println("Transformed import (jdbc): " + importName + " -> " + newImportName);
                        modified = true;
                    }
                }
            }
            
            if (modified) {
                DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
                String transformedCode = printer.print(cu);
                
                // Write back to file
                try {
                    java.io.FileWriter writer = new java.io.FileWriter(javaFile);
                    writer.write(transformedCode);
                    writer.close();
                    System.out.println("Updated: " + javaFile.getPath());
                } catch (java.io.IOException e) {
                    System.err.println("Failed to write file: " + javaFile.getPath() + " - " + e.getMessage());
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("File not found: " + javaFile.getPath());
        } catch (Exception e) {
            System.err.println("Error processing file: " + javaFile.getPath() + " - " + e.getMessage());
        }
    }
}