package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.ImportDeclaration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        File file = new File("/workspace/onebusaway-gtfs-modules/onebusaway-gtfs-hibernate/src/main/java/org/onebusaway/gtfs/impl/ServiceDateUserType.java");
        CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(file));
        
        // Update imports
        for (ImportDeclaration id : cu.findAll(ImportDeclaration.class)) {
            if (id.getNameAsString().equals("org.hibernate.engine.spi.SessionImplementor")) {
                id.setName("org.hibernate.engine.spi.SharedSessionContractImplementor");
            }
        }
        
        // Update parameters
        for (MethodDeclaration md : cu.findAll(MethodDeclaration.class)) {
            if (md.getNameAsString().equals("nullSafeGet") || md.getNameAsString().equals("nullSafeSet")) {
                for (Parameter param : md.getParameters()) {
                    if (param.getType().isClassOrInterfaceType()) {
                        ClassOrInterfaceType type = param.getType().asClassOrInterfaceType();
                        if (type.getNameAsString().equals("SessionImplementor")) {
                            type.setName("SharedSessionContractImplementor");
                        }
                    }
                }
            }
        }
        
        Files.writeString(file.toPath(), cu.toString());
        System.out.println("Transformation applied.");
    }
}
