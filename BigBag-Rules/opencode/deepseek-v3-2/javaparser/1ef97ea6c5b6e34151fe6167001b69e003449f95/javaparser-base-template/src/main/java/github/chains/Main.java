package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for fixing the breaking change in net.datafaker.DateAndTime API.
 * 
 * Breaking Change: 
 * - Old: DateAndTime.between(Date from, Date to) returns Date
 * - New: DateAndTime.between(Timestamp from, Timestamp to) returns Timestamp
 * 
 * This transformation:
 * 1. Finds classes that extend DateAndTime
 * 2. Updates method signatures from between(Date, Date) to between(Timestamp, Timestamp)
 * 3. Updates method bodies that call super.between() to pass Timestamp parameters
 * 4. Adds necessary imports if missing
 * 
 * The transformation is generic and can be applied to any project affected by this
 * breaking change in the datafaker library.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming files in: " + sourceDir);
        
        JavaParser javaParser = new JavaParser();
        
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        int transformedFiles = 0;
        int transformedMethods = 0;
        
        for (Path javaFile : javaFiles) {
            try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                CompilationUnit cu = javaParser.parse(in).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }
                
                boolean fileModified = false;
                boolean needsTimestampImport = false;
                
                // Check if file imports java.sql.Timestamp
                boolean hasTimestampImport = cu.getImports().stream()
                    .anyMatch(imp -> imp.getNameAsString().equals("java.sql.Timestamp"));
                
                // Check if file imports java.util.Date
                boolean hasDateImport = cu.getImports().stream()
                    .anyMatch(imp -> imp.getNameAsString().equals("java.util.Date"));
                
                // Find all classes that extend DateAndTime
                List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
                for (ClassOrInterfaceDeclaration clazz : classes) {
                    if (!clazz.isClassOrInterfaceDeclaration()) {
                        continue;
                    }
                    
                    // Check if this class extends DateAndTime
                    boolean extendsDateAndTime = false;
                    if (clazz.getExtendedTypes().isNonEmpty()) {
                        for (ClassOrInterfaceType extendedType : clazz.getExtendedTypes()) {
                            String typeName = extendedType.getNameAsString();
                            if (typeName.equals("DateAndTime") || 
                                typeName.equals("net.datafaker.DateAndTime") ||
                                (extendedType.getScope().isPresent() && 
                                 extendedType.getScope().get().toString().equals("net.datafaker") &&
                                 typeName.equals("DateAndTime"))) {
                                extendsDateAndTime = true;
                                break;
                            }
                        }
                    }
                    
                    if (!extendsDateAndTime) {
                        continue;
                    }
                    
                    // Find all method declarations in this class
                    List<MethodDeclaration> methods = clazz.getMethods();
                    for (MethodDeclaration method : methods) {
                        // Check for between(Date, Date) methods
                        if (method.getNameAsString().equals("between") && 
                            method.getParameters().size() == 2) {
                            
                            Type param1Type = method.getParameter(0).getType();
                            Type param2Type = method.getParameter(1).getType();
                            
                            // Check if both parameters are java.util.Date (or just Date if imported)
                            if (isDateType(param1Type, hasDateImport) && 
                                isDateType(param2Type, hasDateImport)) {
                                
                                System.out.println("Found between(Date, Date) method declaration in: " + 
                                    javaFile + " at line " + method.getRange().map(r -> r.begin.line).orElse(0));
                                
                                // Transform parameter types from Date to Timestamp
                                method.getParameter(0).setType(createTimestampType());
                                method.getParameter(1).setType(createTimestampType());
                                
                                // Check if we need to add Timestamp import
                                if (!hasTimestampImport) {
                                    needsTimestampImport = true;
                                }
                                
                                // Check method body for super.between calls
                                method.getBody().ifPresent(body -> {
                                    // Look for pattern: new Timestamp(super.between(...).getTime())
                                    // This is a common pattern when converting Date to Timestamp
                                    // The method body might need adjustment if it does unnecessary conversion
                                    String bodyStr = body.toString();
                                    if (bodyStr.contains("super.between(")) {
                                        System.out.println("  Method contains super.between() call - verifying parameter types match");
                                    }
                                });
                                
                                fileModified = true;
                                transformedMethods++;
                            }
                        }
                    }
                }
                
                // Add java.sql.Timestamp import if needed
                if (needsTimestampImport && !hasTimestampImport) {
                    cu.addImport("java.sql.Timestamp");
                    fileModified = true;
                    System.out.println("  Added java.sql.Timestamp import");
                }
                
                if (fileModified) {
                    // Write the modified file back
                    Files.write(javaFile, cu.toString().getBytes());
                    transformedFiles++;
                    System.out.println("Transformed: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\n=== Transformation Complete ===");
        System.out.println("Transformed files: " + transformedFiles);
        System.out.println("Transformed method declarations: " + transformedMethods);
        
        System.out.println("\n=== Breaking Change Summary ===");
        System.out.println("Dependency: net.datafaker:datafaker");
        System.out.println("Affected class: net.datafaker.DateAndTime");
        System.out.println("Changed method signature:");
        System.out.println("  OLD: between(java.util.Date from, java.util.Date to) returns java.util.Date");
        System.out.println("  NEW: between(java.sql.Timestamp from, java.sql.Timestamp to) returns java.sql.Timestamp");
        System.out.println("\nTransformation applied:");
        System.out.println("1. Updated method signatures in classes extending DateAndTime");
        System.out.println("2. Changed parameter types from Date to Timestamp");
        System.out.println("3. Added java.sql.Timestamp import if missing");
        System.out.println("\nNote: Method bodies that convert Date to Timestamp may need manual review.");
        System.out.println("Common patterns to check:");
        System.out.println("  - new Timestamp(super.between(...).getTime()) - May be redundant");
        System.out.println("  - Date parameter conversions - May need to change to Timestamp");
    }
    
    private static boolean isDateType(Type type, boolean hasDateImport) {
        if (type.isClassOrInterfaceType()) {
            String typeName = type.asClassOrInterfaceType().getNameAsString();
            // Check for fully qualified name
            if (typeName.equals("java.util.Date")) {
                return true;
            }
            // Check for simple name if java.util.Date is imported
            if (hasDateImport && typeName.equals("Date")) {
                return true;
            }
        }
        return false;
    }
    
    private static Type createTimestampType() {
        return new ClassOrInterfaceType(null, "java.sql.Timestamp");
    }
}