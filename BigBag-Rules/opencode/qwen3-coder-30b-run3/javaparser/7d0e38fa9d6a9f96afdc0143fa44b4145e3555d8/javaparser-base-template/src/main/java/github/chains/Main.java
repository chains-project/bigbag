package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.NodeList;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    
    public static void main(String[] args) {
        System.out.println("JavaParser transformation initialized");
    }
}
            
            processDirectory(sourceDir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(String dirPath) throws IOException {
        Path dir = Paths.get(dirPath);
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformations for each breaking change pattern
            applyManagedZonesCreateFix(cu);
            applyManagedZonesGetFix(cu);
            applyManagedZonesListFix(cu);
            applyManagedZonesDeleteFix(cu);
            applyResourceRecordSetsListFix(cu);
            applyProjectsGetFix(cu);
            applyChangesCreateFix(cu);
            applyChangesGetFix(cu);
            applyChangesListFix(cu);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                cu.accept(new com.github.javaparser.printer.JavaPrinter(), writer);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    // Fix for ManagedZones.create method calls
    private static void applyManagedZonesCreateFix(CompilationUnit cu) {
        // Pattern: managedZones().create(project, zone)
        // Should become: managedZones().create(project, managedZone, zone)
        cu.findAll(MethodCallExpr.class).stream()
            .filter(call -> 
                call.getScope().isPresent() &&
                call.getScope().get() instanceof MethodCallExpr &&
                ((MethodCallExpr) call.getScope().get()).getNameAsString().equals("managedZones") &&
                call.getNameAsString().equals("create") &&
                call.getArguments().size() == 2)
            .forEach(call -> {
                // Insert a dummy string as the second parameter (location)
                NodeList<Expression> args = call.getArguments();
                args.add(1, new StringLiteralExpr(""));
            });
    }
    
    // Fix for ManagedZones.get method calls
    private static void applyManagedZonesGetFix(CompilationUnit cu) {
        // Pattern: managedZones().get(project, managedZone)
        // Should become: managedZones().get(project, managedZone, clientOperationId)
        cu.findAll(MethodCallExpr.class).stream()
            .filter(call -> 
                call.getScope().isPresent() &&
                call.getScope().get() instanceof MethodCallExpr &&
                ((MethodCallExpr) call.getScope().get()).getNameAsString().equals("managedZones") &&
                call.getNameAsString().equals("get") &&
                call.getArguments().size() == 2)
            .forEach(call -> {
                // Insert a dummy string as the third parameter (clientOperationId)
                NodeList<Expression> args = call.getArguments();
                args.add(2, new StringLiteralExpr(""));
            });
    }
    
    // Fix for ManagedZones.list method calls
    private static void applyManagedZonesListFix(CompilationUnit cu) {
        // Pattern: managedZones().list(project)
        // Should become: managedZones().list(project, managedZone, location)
        cu.findAll(MethodCallExpr.class).stream()
            .filter(call -> 
                call.getScope().isPresent() &&
                call.getScope().get() instanceof MethodCallExpr &&
                ((MethodCallExpr) call.getScope().get()).getNameAsString().equals("managedZones") &&
                call.getNameAsString().equals("list") &&
                call.getArguments().size() == 1)
            .forEach(call -> {
                // Insert two dummy strings as additional parameters (managedZone, location)
                NodeList<Expression> args = call.getArguments();
                args.add(1, new StringLiteralExpr(""));
                args.add(2, new StringLiteralExpr(""));
            });
    }
    
    // Fix for ManagedZones.delete method calls
    private static void applyManagedZonesDeleteFix(CompilationUnit cu) {
        cu.findAll(MethodCallExpr.class).stream()
            .filter(call -> 
                call.getScope().isPresent() &&
                call.getScope().get() instanceof MethodCallExpr &&
                ((MethodCallExpr) call.getScope().get()).getNameAsString().equals("managedZones") &&
                call.getNameAsString().equals("delete") &&
                call.getArguments().size() == 2)
            .forEach(call -> {
                // Insert a dummy string as the third parameter (clientOperationId)
                NodeList<Expression> args = call.getArguments();
                args.add(2, new StringLiteralExpr(""));
            });
    }
    
    // Fix for ResourceRecordSets.list method calls
    private static void applyResourceRecordSetsListFix(CompilationUnit cu) {
        // Pattern: resourceRecordSets().list(project, managedZone)
        // Should become: resourceRecordSets().list(project, managedZone, location)
        cu.findAll(MethodCallExpr.class).stream()
            .filter(call -> 
                call.getScope().isPresent() &&
                call.getScope().get() instanceof MethodCallExpr &&
                ((MethodCallExpr) call.getScope().get()).getNameAsString().equals("resourceRecordSets") &&
                call.getNameAsString().equals("list") &&
                call.getArguments().size() == 2)
            .forEach(call -> {
                // Insert a dummy string as the third parameter (location)
                NodeList<Expression> args = call.getArguments();
                args.add(2, new StringLiteralExpr(""));
            });
    }
    
    // Fix for Projects.get method calls
    private static void applyProjectsGetFix(CompilationUnit cu) {
        // Pattern: projects().get(project)
        // Should become: projects().get(project, location)
        cu.findAll(MethodCallExpr.class).stream()
            .filter(call -> 
                call.getScope().isPresent() &&
                call.getScope().get() instanceof MethodCallExpr &&
                ((MethodCallExpr) call.getScope().get()).getNameAsString().equals("projects") &&
                call.getNameAsString().equals("get") &&
                call.getArguments().size() == 1)
            .forEach(call -> {
                // Insert a dummy string as the second parameter (location)
                NodeList<Expression> args = call.getArguments();
                args.add(1, new StringLiteralExpr(""));
            });
    }
    
    // Fix for Changes.create method calls
    private static void applyChangesCreateFix(CompilationUnit cu) {
        // Pattern: changes().create(project, managedZone, change)
        // Should become: changes().create(project, managedZone, clientOperationId, change)
        cu.findAll(MethodCallExpr.class).stream()
            .filter(call -> 
                call.getScope().isPresent() &&
                call.getScope().get() instanceof MethodCallExpr &&
                ((MethodCallExpr) call.getScope().get()).getNameAsString().equals("changes") &&
                call.getNameAsString().equals("create") &&
                call.getArguments().size() == 3)
            .forEach(call -> {
                // Insert a dummy string as the third parameter (clientOperationId)
                NodeList<Expression> args = call.getArguments();
                args.add(2, new StringLiteralExpr(""));
            });
    }
    
    // Fix for Changes.get method calls
    private static void applyChangesGetFix(CompilationUnit cu) {
        // Pattern: changes().get(project, managedZone, changeId)
        // Should become: changes().get(project, managedZone, clientOperationId, changeId)
        cu.findAll(MethodCallExpr.class).stream()
            .filter(call -> 
                call.getScope().isPresent() &&
                call.getScope().get() instanceof MethodCallExpr &&
                ((MethodCallExpr) call.getScope().get()).getNameAsString().equals("changes") &&
                call.getNameAsString().equals("get") &&
                call.getArguments().size() == 3)
            .forEach(call -> {
                // Insert a dummy string as the third parameter (clientOperationId)
                NodeList<Expression> args = call.getArguments();
                args.add(2, new StringLiteralExpr(""));
            });
    }
    
    // Fix for Changes.list method calls
    private static void applyChangesListFix(CompilationUnit cu) {
        // Pattern: changes().list(project, managedZone)
        // Should become: changes().list(project, managedZone, location)
        cu.findAll(MethodCallExpr.class).stream()
            .filter(call -> 
                call.getScope().isPresent() &&
                call.getScope().get() instanceof MethodCallExpr &&
                ((MethodCallExpr) call.getScope().get()).getNameAsString().equals("changes") &&
                call.getNameAsString().equals("list") &&
                call.getArguments().size() == 2)
            .forEach(call -> {
                // Insert a dummy string as the third parameter (location)
                NodeList<Expression> args = call.getArguments();
                args.add(2, new StringLiteralExpr(""));
            });
    }
}
