package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        File file = new File("/workspace/snmpman/snmpman/src/main/java/com/oneandone/snmpman/SnmpmanAgent.java");
        CompilationUnit cu = StaticJavaParser.parse(file);

        cu.findAll(MethodDeclaration.class, m -> m.getNameAsString().equals("registerHard"))
            .forEach(m -> {
                m.findAll(VariableDeclarator.class, v -> v.getNameAsString().equals("reg"))
                    .forEach(v -> {
                        // Change type from SortedMap<MOScope, ManagedObject> to SortedMap<MOScope, ManagedObject<?>>
                        Type type = v.getType();
                        if (type.isClassOrInterfaceType()) {
                            ClassOrInterfaceType sortedMapType = type.asClassOrInterfaceType();
                            if (sortedMapType.getNameAsString().equals("SortedMap") && sortedMapType.getTypeArguments().isPresent()) {
                                var typeArgs = sortedMapType.getTypeArguments().get();
                                if (typeArgs.size() == 2) {
                                    Type valueType = typeArgs.get(1);
                                    if (valueType.isClassOrInterfaceType() && valueType.asClassOrInterfaceType().getNameAsString().equals("ManagedObject")) {
                                         // Create ManagedObject<?>
                                         ClassOrInterfaceType managedObjectWildcard = new ClassOrInterfaceType(null, "ManagedObject");
                                         managedObjectWildcard.setTypeArguments(new WildcardType());
                                         
                                         // Replace the type argument
                                         typeArgs.set(1, managedObjectWildcard);
                                         System.out.println("Modified type for 'reg' to: " + sortedMapType);
                                    }
                                }
                            }
                        }
                    });
            });

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(cu.toString());
        }
    }
}