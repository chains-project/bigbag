package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    private static String oldPackagePrefix = "com.mysql.jdbc.";
    private static String newPackagePrefix = "com.mysql.cj.jdbc.";
    
    public static void transformFile(Path filePath) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow();
        
        boolean[] modifiedHolder = new boolean[]{false};
        
        NodeList<ImportDeclaration> imports = cu.getImports();
        for (ImportDeclaration importDecl : imports) {
            String importName = importDecl.getNameAsString();
            
            if (importName.startsWith(oldPackagePrefix)) {
                String newImportName = importName.replace(oldPackagePrefix, newPackagePrefix);
                importDecl.setName(new Name(newImportName));
                modifiedHolder[0] = true;
                System.out.println("Updated import: " + importName + " -> " + newImportName);
            }
        }
        
        cu.walk(ClassOrInterfaceType.class, classType -> {
            String typeName = classType.getNameAsString();
            if (typeName.startsWith(oldPackagePrefix)) {
                String newTypeName = typeName.replace(oldPackagePrefix, newPackagePrefix);
                classType.setName(newTypeName);
                System.out.println("Updated type reference: " + typeName + " -> " + newTypeName);
                modifiedHolder[0] = true;
            }
        });
        
        cu.walk(com.github.javaparser.ast.expr.FieldAccessExpr.class, fieldAccess -> {
            String fieldAccessStr = fieldAccess.toString();
            if (fieldAccessStr.contains(oldPackagePrefix)) {
                String newFieldAccessStr = fieldAccessStr.replace(oldPackagePrefix, newPackagePrefix);
                fieldAccess.replace(new com.github.javaparser.JavaParser().parseExpression(newFieldAccessStr).getResult().get());
                System.out.println("Updated field access: " + fieldAccessStr + " -> " + newFieldAccessStr);
                modifiedHolder[0] = true;
            }
        });
        
        cu.walk(com.github.javaparser.ast.expr.StringLiteralExpr.class, stringLiteral -> {
            String literalValue = stringLiteral.getValue();
            if (literalValue.contains(oldPackagePrefix)) {
                String newLiteralValue = literalValue.replace(oldPackagePrefix, newPackagePrefix);
                stringLiteral.setString(newLiteralValue);
                System.out.println("Updated string literal: " + literalValue + " -> " + newLiteralValue);
                modifiedHolder[0] = true;
            }
        });
        
        if (modifiedHolder[0]) {
            Files.write(filePath, cu.toString().getBytes());
            System.out.println("Updated file: " + filePath);
        }
    }
    
    public static void transformDirectory(Path directory) throws Exception {
        List<Path> javaFiles = Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        for (Path javaFile : javaFiles) {
            transformFile(javaFile);
        }
    }
    
    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java -jar javaparser.jar <directory> <oldPackagePrefix> <newPackagePrefix>");
            System.err.println("Example: java -jar javaparser.jar /path/to/src com.mysql.jdbc. com.mysql.cj.jdbc.");
            System.exit(1);
        }
        
        Path path = Paths.get(args[0]);
        oldPackagePrefix = args[1];
        newPackagePrefix = args[2];
        
        try {
            if (Files.isDirectory(path)) {
                transformDirectory(path);
                System.out.println("Transformation completed successfully for directory!");
            } else if (Files.isRegularFile(path) && path.toString().endsWith(".java")) {
                transformFile(path);
                System.out.println("Transformation completed successfully for file!");
            } else {
                System.err.println("Error: " + path + " is not a directory or Java file");
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
