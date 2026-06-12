package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/open-pdf-sign/src/main/java/org/openpdfsign/Signer.java");
        
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        // 1. Remove the usage
        List<CtInvocation> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        boolean usageRemoved = false;
        for (CtInvocation invocation : invocations) {
             if ("setPermission".equals(invocation.getExecutable().getSimpleName())) {
                 if (invocation.getArguments().size() > 0 && 
                     invocation.getArguments().get(0).toString().contains("CertificationPermission")) {
                     if (invocation.getParent() != null) {
                        invocation.getParent().delete();
                        System.out.println("Removed setPermission usage.");
                        usageRemoved = true;
                     }
                 }
             }
        }
        
        // 2. Remove the import
        boolean importRemoved = false;
        for (CtType<?> type : model.getAllTypes()) {
            if (type.getSimpleName().equals("Signer")) {
                 CtCompilationUnit compilationUnit = type.getPosition().getCompilationUnit();
                 if (compilationUnit != null) {
                     List<CtImport> importsToRemove = new ArrayList<>();
                     for (CtImport imp : compilationUnit.getImports()) {
                         String importStr = imp.toString();
                         if (importStr.contains("CertificationPermission")) {
                             importsToRemove.add(imp);
                         }
                     }
                     for (CtImport imp : importsToRemove) {
                         compilationUnit.getImports().remove(imp);
                         System.out.println("Removed import: " + imp);
                         importRemoved = true;
                     }
                     
                     // Only write if changes were made
                     if (usageRemoved || importRemoved) {
                         try (java.io.PrintWriter out = new java.io.PrintWriter("/workspace/open-pdf-sign/src/main/java/org/openpdfsign/Signer.java")) {
                             out.print(compilationUnit.prettyprint());
                             System.out.println("Rewrote Signer.java");
                         }
                     } else {
                         System.out.println("No changes made to Signer.java");
                     }
                 }
            }
        }
    }
}