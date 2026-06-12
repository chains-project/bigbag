package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.declaration.CtType;
import spoon.compiler.Environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws IOException {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/allure-maven/src/main/java/io/qameta/allure/maven/AllureCommandline.java");
        launcher.setSourceOutputDirectory("/workspace/allure-maven/src/main/java");
        Environment env = launcher.getEnvironment();
        env.setNoClasspath(true);
        env.setAutoImports(true);
        
        launcher.buildModel();
        
        for (CtTypeReference<?> ref : launcher.getFactory().getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            if ("net.lingala.zip4j.core.ZipFile".equals(ref.getQualifiedName())) {
                ref.setPackage(launcher.getFactory().Package().createReference("net.lingala.zip4j"));
            }
        }

        for (CtType<?> type : launcher.getFactory().getModel().getAllTypes()) {
            CtCompilationUnit cu = type.getPosition().getCompilationUnit();
            var newImports = cu.getImports().stream()
                .filter(imp -> !imp.toString().contains("net.lingala.zip4j.core.ZipFile"))
                .collect(Collectors.toList());
            cu.setImports(newImports);
        }
        
        launcher.prettyprint();
        
        // Post-process to remove unnecessary parentheses for checkstyle
        String path = "/workspace/allure-maven/src/main/java/io/qameta/allure/maven/AllureCommandline.java";
        String code = Files.readString(Paths.get(path));
        code = code.replace("(serveHost != null) &&", "serveHost != null &&");
        code = code.replace("&& (version != null)", "&& version != null");
        code = code.replace("(mavenProxy != null) &&", "mavenProxy != null &&");
        code = code.replace("(mavenProxy.getUsername() != null) &&", "mavenProxy.getUsername() != null &&");
        code = code.replace("&& (mavenProxy.getPassword() != null)", "&& mavenProxy.getPassword() != null");
        Files.writeString(Paths.get(path), code);
    }
}