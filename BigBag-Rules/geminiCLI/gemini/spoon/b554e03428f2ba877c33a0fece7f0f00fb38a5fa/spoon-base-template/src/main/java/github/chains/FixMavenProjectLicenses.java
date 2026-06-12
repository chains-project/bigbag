package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;

import java.util.Collections;

public class FixMavenProjectLicenses {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/license-maven-plugin/license-maven-plugin/src/main/java/com/mycila/maven/plugin/license/dependencies/MavenProjectLicenses.java");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        CtClass<?> clazz = launcher.getFactory().Class().get("com.mycila.maven.plugin.license.dependencies.MavenProjectLicenses");
        
        CtMethod<?> setGraphMethod = clazz.getMethodsByName("setGraph").get(0);
        
        // Replace body with "this.graph = graph;"
        setGraphMethod.getBody().setStatements(Collections.singletonList(
            factory.Code().createCodeSnippetStatement("this.graph = graph")
        ));
        
        // Remove the unused import
        spoon.reflect.declaration.CtCompilationUnit cu = clazz.getPosition().getCompilationUnit();
        if (cu != null) {
            java.util.List<spoon.reflect.declaration.CtImport> importsToRemove = new java.util.ArrayList<>();
            for (spoon.reflect.declaration.CtImport imp : cu.getImports()) {
                if (imp.getReference() != null && "Maven31DependencyGraphBuilder".equals(imp.getReference().getSimpleName())) {
                    importsToRemove.add(imp);
                }
            }
            for (spoon.reflect.declaration.CtImport imp : importsToRemove) {
                cu.getImports().remove(imp);
            }
        }
        
        launcher.setSourceOutputDirectory("/workspace/license-maven-plugin/license-maven-plugin/src/main/java");
        launcher.prettyprint();
    }
}
