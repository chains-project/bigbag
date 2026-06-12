package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class SpoonFixer {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/myfaces-tobago/tobago-tool/tobago-theme-plugin/src/main/java");
        launcher.setSourceOutputDirectory("/workspace/myfaces-tobago/tobago-tool/tobago-theme-plugin/src/main/java");
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);

        launcher.buildModel();
        CtModel model = launcher.getModel();
        Factory factory = launcher.getFactory();

        // Fix AbstractThemeMojo
        CtClass<?> abstractThemeMojo = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> element) {
                return "org.apache.myfaces.tobago.maven.plugin.AbstractThemeMojo".equals(element.getQualifiedName());
            }
        }).stream().findFirst().orElseThrow(() -> new RuntimeException("AbstractThemeMojo not found"));

        // Change field type from MavenProject to Object
        CtField<?> projectField = abstractThemeMojo.getField("project");
        if (projectField != null) {
            projectField.setType(factory.Type().objectType());
        }

        // Change getProject return type
        CtMethod<?> getProjectMethod = abstractThemeMojo.getMethodsByName("getProject").stream().findFirst().orElse(null);
        if (getProjectMethod != null) {
            getProjectMethod.setType(factory.Type().objectType());
        }

        // Fix UnPackThemeMojo
        CtClass<?> unPackThemeMojo = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> element) {
                return "org.apache.myfaces.tobago.maven.plugin.UnPackThemeMojo".equals(element.getQualifiedName());
            }
        }).stream().findFirst().orElseThrow(() -> new RuntimeException("UnPackThemeMojo not found"));

        // In execute method
        CtMethod<?> executeMethod = unPackThemeMojo.getMethodsByName("execute").stream().findFirst().orElse(null);
        if (executeMethod != null) {
            List<CtInvocation<?>> invocations = executeMethod.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class));
            for (CtInvocation<?> inv : invocations) {
                if ("getRuntimeClasspathElements".equals(inv.getExecutable().getSimpleName())) {
                    // Target replacement: ((java.util.List<String>) getProject().getClass().getMethod("getRuntimeClasspathElements").invoke(getProject()))
                    CtCodeSnippetExpression<Object> reflectionCall = factory.Code().createCodeSnippetExpression(
                        "((java.util.List<String>) getProject().getClass().getMethod(\"getRuntimeClasspathElements\").invoke(getProject()))"
                    );
                    inv.replace(reflectionCall);
                }
            }
            
            // Handle catch(DependencyResolutionRequiredException drre)
            List<CtCatch> catches = executeMethod.getElements(new TypeFilter<>(CtCatch.class));
            for (CtCatch catchBlock : catches) {
                 CtCatchVariable<?> param = catchBlock.getParameter();
                 // Check if the type name matches what we expect
                 if (param.getType().getSimpleName().equals("DependencyResolutionRequiredException")) {
                     param.setType(factory.Type().createReference("java.lang.Exception"));
                 }
            }
        }

        // Manually remove imports
        for (spoon.reflect.cu.CompilationUnit cu : factory.CompilationUnit().getMap().values()) {
            List<spoon.reflect.declaration.CtImport> importsToRemove = new java.util.ArrayList<>();
            for (spoon.reflect.declaration.CtImport imp : cu.getImports()) {
                String s = imp.toString();
                if (s.contains("MavenProject") || s.contains("DependencyResolutionRequiredException")) {
                    importsToRemove.add(imp);
                    System.out.println("Marked for removal: " + s);
                }
            }
            for (spoon.reflect.declaration.CtImport imp : importsToRemove) {
                boolean removed = cu.getImports().remove(imp);
                System.out.println("Removed " + imp.toString() + ": " + removed);
            }
        }
        
        spoon.support.JavaOutputProcessor processor = new spoon.support.JavaOutputProcessor(launcher.createPrettyPrinter());
        processor.setFactory(factory);
        for (CtType<?> type : model.getAllTypes()) {
            processor.createJavaFile(type);
        }
    }
}
