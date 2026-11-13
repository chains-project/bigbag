package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 5) {
            System.err.println("Usage: java github.chains.Main <oldFullyQualifiedType> <newFullyQualifiedType> <oldMethodName> <newMethodName> <sourceDirectory>");
            System.err.println("Example: java github.chains.Main de.gwdg.metadataqa.api.json.JsonBranch de.gwdg.metadataqa.api.json.DataElement getJsonPath getPath /path/to/project/src");
            System.exit(1);
        }
        
        String oldType = args[0];
        String newType = args[1];
        String oldMethod = args[2];
        String newMethod = args[3];
        String sourceDir = args[4];
        
        System.out.println("Transforming " + oldType + " -> " + newType);
        System.out.println("Method rename: " + oldMethod + "() -> " + newMethod + "()");
        System.out.println("Source directory: " + sourceDir);
        
        try {
            transformProject(oldType, newType, oldMethod, newMethod, sourceDir);
            System.out.println("Transformation complete.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String oldType, String newType, String oldMethod, String newMethod, String sourceDir) throws Exception {
        File dir = new File(sourceDir);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDir);
        }
        
        List<File> javaFiles = findJavaFiles(dir);
        System.out.println("Found " + javaFiles.size() + " Java files to process.");
        
        int transformedFiles = 0;
        for (File javaFile : javaFiles) {
            if (transformFile(javaFile, oldType, newType, oldMethod, newMethod)) {
                transformedFiles++;
            }
        }
        
        System.out.println("Transformed " + transformedFiles + " files.");
    }
    
    private static List<File> findJavaFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    javaFiles.addAll(findJavaFiles(file));
                } else if (file.getName().endsWith(".java")) {
                    javaFiles.add(file);
                }
            }
        }
        return javaFiles;
    }
    
    private static boolean transformFile(File javaFile, String oldType, String newType, String oldMethod, String newMethod) throws FileNotFoundException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();
        
        boolean[] wasModified = new boolean[]{false};
        
        VoidVisitorAdapter<Void> visitor = new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ImportDeclaration importDecl, Void arg) {
                String importName = importDecl.getNameAsString();
                if (importName.equals(oldType)) {
                    wasModified[0] = true;
                    importDecl.setName(newType);
                }
                super.visit(importDecl, arg);
            }
            
            @Override
            public void visit(ClassOrInterfaceType type, Void arg) {
                String typeName = type.getNameAsString();
                if (typeName.equals(getSimpleName(oldType))) {
                    wasModified[0] = true;
                    type.setName(getSimpleName(newType));
                }
                super.visit(type, arg);
            }
            
            @Override
            public void visit(MethodCallExpr methodCall, Void arg) {
                String methodName = methodCall.getNameAsString();
                if (methodName.equals(oldMethod)) {
                    wasModified[0] = true;
                    methodCall.setName(newMethod);
                }
                super.visit(methodCall, arg);
            }
        };
        
        visitor.visit(cu, null);
        
        if (wasModified[0]) {
            cu.getStorage().ifPresent(storage -> {
                try {
                    storage.save();
                    System.out.println("Transformed: " + javaFile.getPath());
                } catch (Exception e) {
                    System.err.println("Error saving file " + javaFile.getPath() + ": " + e.getMessage());
                }
            });
            return true;
        }
        return false;
    }
    
    private static String getSimpleName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        if (lastDot >= 0) {
            return fullyQualifiedName.substring(lastDot + 1);
        }
        return fullyQualifiedName;
    }
}