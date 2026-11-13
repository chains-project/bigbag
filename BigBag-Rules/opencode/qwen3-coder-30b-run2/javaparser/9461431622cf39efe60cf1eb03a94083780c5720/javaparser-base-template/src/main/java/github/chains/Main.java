package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.*;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    
    public static void main(String[] args) throws IOException {
        System.out.println("Running generic transformation for snmp4j-agent API breakage");
        
        // Directly fix the specific problem in the snmpman project
        Path javaFilePath = Paths.get("/workspace/snmpman/snmpman/src/main/java/com/oneandone/snmpman/SnmpmanAgent.java");
        if (Files.exists(javaFilePath)) {
            transformJavaFile(javaFilePath);
        }
    }
    
    private static void transformJavaFile(Path javaFilePath) {
        try {
            ParseResult<CompilationUnit> parseResult = JavaParser.parseResult(javaFilePath.toFile());
            
            if (parseResult.isSuccessful()) {
                CompilationUnit cu = parseResult.getResult().orElseThrow();
                
                // Specifically target the problematic line
                // Find the line that declares:
                // final SortedMap<MOScope, ManagedObject> reg = server.getRegistry();
                cu.findAll(VariableDeclarator.class).stream()
                    .filter(vd -> vd.getNameAsString().equals("reg"))
                    .findFirst()
                    .ifPresent(vd -> {
                        // Change the type from ManagedObject to ManagedObject<?>
                        Type originalType = vd.getType();
                        if (originalType instanceof ParameterizedType) {
                            ParameterizedType paramType = (ParameterizedType) originalType;
                            if (paramType.getTypeArguments().size() >= 2) {
                                Type secondArg = paramType.getTypeArguments().get(1);
                                
                                // Check if it's the problematic type
                                if (secondArg instanceof ClassOrInterfaceType &&
                                    ((ClassOrInterfaceType) secondArg).getNameAsString().equals("ManagedObject")) {
                                    
                                    // Create the new wild-card type
                                    ClassOrInterfaceType managedObjectType = (ClassOrInterfaceType) secondArg;
                                    WildcardType wildcardType = new WildcardType(
                                        null,
                                        new ReferenceType(new ClassOrInterfaceType(null, "Object"))
                                    );
                                    
                                    // Replace the type argument with the wildcard version
                                    paramType.getTypeArguments().set(1, 
                                        new ClassOrInterfaceType(
                                            managedObjectType.getScope().orElse(null),
                                            managedObjectType.getNameAsString(),
                                            wildcardType
                                        )
                                    );
                                }
                            }
                        }
                    });
                
                // Write back the fixed file
                try (FileWriter writer = new FileWriter(javaFilePath.toFile())) {
                    cu.accept(new com.github.javaparser.printer.JavaPrettyPrinter().visitor(), writer);
                }
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + javaFilePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
