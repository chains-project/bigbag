package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.factory.Factory;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.declaration.CtCompilationUnit;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        
        // Add specific files to fix
        launcher.addInputResource("/workspace/jcabi-s3/src/test/java/com/jcabi/s3/BucketRule.java");
        launcher.addInputResource("/workspace/jcabi-s3/src/test/java/com/jcabi/s3/AwsOcketITCase.java");
        
        // Set output directory to overwrite
        launcher.setSourceOutputDirectory("/workspace/jcabi-s3/src/test/java");
        
        // Configure environment
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build model
        launcher.buildModel();
        
        Factory factory = launcher.getFactory();
        
        // Process replacements
        launcher.getModel().getRootPackage().accept(new CtScanner() {
            @Override
            public <T> void visitCtFieldRead(CtFieldRead<T> fieldRead) {
                super.visitCtFieldRead(fieldRead);
                CtFieldReference<T> ref = fieldRead.getVariable();
                if (ref.getDeclaringType() != null && "com.jcabi.aspects.Tv".equals(ref.getDeclaringType().getQualifiedName())) {
                    String name = ref.getSimpleName();
                    CtExpression replacement = null;
                    if ("FIVE".equals(name)) {
                        replacement = factory.Code().createLiteral(5);
                    } else if ("HUNDRED".equals(name)) {
                        replacement = factory.Code().createLiteral(100);
                    } else if ("THOUSAND".equals(name)) {
                        replacement = factory.Code().createLiteral(1000);
                    }
                    
                    if (replacement != null) {
                        fieldRead.replace(replacement);
                    }
                }
            }
        });

        // Remove imports directly from Compilation Units
        for (CtCompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
             List<CtImport> importsToRemove = new ArrayList<>();
             for (CtImport imp : cu.getImports()) {
                 // Check if it matches com.jcabi.aspects.Tv
                 if (imp.toString().contains("com.jcabi.aspects.Tv")) {
                     importsToRemove.add(imp);
                 }
             }
             if (!importsToRemove.isEmpty()) {
                 cu.getImports().removeAll(importsToRemove);
             }
        }
        
        // Save changes
        launcher.prettyprint();
    }
}
