package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;

import java.util.Collections;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        // Only target the file we want to modify to avoid rewriting others
        launcher.addInputResource("/workspace/biapi/src/main/java/xdev/tableexport/export/ReportBuilder.java");
        
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Output to the same directory to overwrite
        launcher.setSourceOutputDirectory("/workspace/biapi/src/main/java"); 
        
        launcher.buildModel();
        CtModel model = launcher.getModel();
        Factory factory = launcher.getFactory();

        CtClass<?> reportBuilder = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> element) {
                return "xdev.tableexport.export.ReportBuilder".equals(element.getQualifiedName());
            }
        }).stream().findFirst().orElseThrow(() -> new RuntimeException("Class not found: ReportBuilder"));

        List<CtInvocation<?>> invocations = reportBuilder.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class));
        
        boolean modified = false;
        
        for (CtInvocation<?> invocation : invocations) {
            if ("setLineWidth".equals(invocation.getExecutable().getSimpleName())) {
                 if (invocation.getArguments().size() == 1) {
                     CtExpression<?> arg = invocation.getArguments().get(0);
                     if (arg instanceof CtInvocation) {
                         CtInvocation<?> argInv = (CtInvocation<?>) arg;
                         if ("getLineWidth".equals(argInv.getExecutable().getSimpleName())) {
                             System.out.println("Found setLineWidth call at line " + invocation.getPosition().getLine());
                             
                             // Create (float) cast using CodeSnippet
                             spoon.reflect.code.CtCodeSnippetExpression<Float> castSnippet = factory.Code().createCodeSnippetExpression("(float) " + arg.toString());
                             
                             invocation.setArguments(Collections.singletonList(castSnippet));
                             modified = true;
                         }
                     }
                 }
            }
        }
        
        if (modified) {
            launcher.prettyprint();
            System.out.println("Transformation applied to ReportBuilder.java");
        } else {
            System.out.println("No matching invocation found in ReportBuilder.java");
        }
    }
}
