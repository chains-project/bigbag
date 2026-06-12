package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtThrow;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HapJavaFix {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        // Only process the specific file to avoid parsing everything and potentially messing up other things
        launcher.addInputResource("/workspace/HAP-Java/src/main/java/io/github/hapjava/server/impl/crypto/ChachaDecoder.java");
        
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        // We will output to the same directory to overwrite
        launcher.setSourceOutputDirectory("/workspace/HAP-Java/src/main/java");
        
        launcher.addProcessor(new AbstractProcessor<CtClass<?>>() {
            @Override
            public void process(CtClass<?> ctClass) {
                if (!"io.github.hapjava.server.impl.crypto.ChachaDecoder".equals(ctClass.getQualifiedName())) {
                    return;
                }

                // Remove imports
                // We need to work on a copy or iterator to avoid concurrent modification if we were iterating
                // But removeIf is safe.
                try {
                     List<CtImport> imports = new ArrayList<>(ctClass.getPosition().getCompilationUnit().getImports());
                     for (CtImport imp : imports) {
                         if (imp.getReference() != null && imp.getReference().toString().startsWith("org.bouncycastle.crypto.tls")) {
                             ctClass.getPosition().getCompilationUnit().getImports().remove(imp);
                         }
                     }
                } catch (Exception e) {
                    System.err.println("Error processing imports: " + e.getMessage());
                    e.printStackTrace();
                }

                // Replace throw statement
                List<CtThrow> throwsStatements = ctClass.getElements(new TypeFilter<>(CtThrow.class));
                for (CtThrow throwStmt : throwsStatements) {
                    if (throwStmt.getThrownExpression().toString().contains("TlsFatalAlert")) {
                        CtConstructorCall<IOException> newException = getFactory().Code().createConstructorCall(
                                getFactory().Type().createReference(IOException.class),
                                getFactory().Code().createLiteral("Bad record MAC")
                        );
                        throwStmt.setThrownExpression(newException);
                    }
                }
            }
        });

        launcher.run();
    }
}
