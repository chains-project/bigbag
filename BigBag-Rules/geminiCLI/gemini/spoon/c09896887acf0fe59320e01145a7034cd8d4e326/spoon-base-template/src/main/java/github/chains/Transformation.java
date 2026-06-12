package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.factory.Factory;

public class Transformation {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        // Path to the source file causing error
        launcher.addInputResource("../incrementals-tools/lib/src/main/java/io/jenkins/tools/incrementals/lib/UpdateChecker.java");
        // Output to the same directory to overwrite
        launcher.setSourceOutputDirectory("../incrementals-tools/lib/src/main/java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);

        launcher.buildModel();
        Factory factory = launcher.getFactory();

        launcher.getModel().getRootPackage().accept(new CtScanner() {
            @Override
            public <T> void visitCtFieldRead(CtFieldRead<T> fieldRead) {
                super.visitCtFieldRead(fieldRead);
                
                if (fieldRead.getVariable().getSimpleName().equals("status")) {
                     // We check if the type of the target seems to be GHCompare, or simply we assume it is valid context.
                     // Since we don't have classpath, we can check if the target looks like an invocation that returns GHCompare
                     // Or just blindly replace .status with .getStatus() if it looks like the specific line.
                     
                     // The line is: GHCompare.Status status = GitHub.connect().getRepository(ghc.owner + '/' + ghc.repo).getCompare(branch, ghc.hash).status;
                     // The target is ...getCompare(...)
                     
                     if (fieldRead.getTarget() instanceof CtInvocation) {
                         CtInvocation targetInv = (CtInvocation) fieldRead.getTarget();
                         if (targetInv.getExecutable().getSimpleName().equals("getCompare")) {
                             
                            CtExecutableReference<Object> executableRef = factory.Core().createExecutableReference();
                            executableRef.setSimpleName("getStatus");
                            // We can try to set declaring type if we knew it, but for method call generation it might be optional in noclasspath mode 
                            // or inferred during printing.
                            
                            CtInvocation invocation = factory.Code().createInvocation(
                                fieldRead.getTarget(),
                                executableRef
                            );
                            
                            fieldRead.replace(invocation);
                            System.out.println("Replaced .status with .getStatus() at line " + fieldRead.getPosition().getLine());
                         }
                     }
                }
            }
        });

        launcher.prettyprint();
    }
}
