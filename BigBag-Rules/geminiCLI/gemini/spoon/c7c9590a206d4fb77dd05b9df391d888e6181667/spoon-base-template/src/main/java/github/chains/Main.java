package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/scoverage-maven-plugin/src/main/java/org/scoverage/plugin/SCoverageReportMojo.java");
        launcher.setSourceOutputDirectory("/workspace/scoverage-maven-plugin/src/main/java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setAutoImports(true);

        launcher.buildModel();

        // 1. Fix the reference package
        for (CtTypeReference<?> ref : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            if ("org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext".equals(ref.getQualifiedName())) {
                ref.setPackage(launcher.getFactory().Package().createReference("org.apache.maven.doxia.siterenderer"));
                System.out.println("Fixed reference");
            }
        }

        // 2. Remove the unused import
        for (CtCompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
            boolean removed = cu.getImports().removeIf(imp -> 
                imp.getReference() != null && 
                imp.getReference().toString().equals("org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext")
            );
            if (removed) {
                System.out.println("Removed unused import from " + cu.getFile().getName());
            }
        }
        
        launcher.prettyprint();
    }
}