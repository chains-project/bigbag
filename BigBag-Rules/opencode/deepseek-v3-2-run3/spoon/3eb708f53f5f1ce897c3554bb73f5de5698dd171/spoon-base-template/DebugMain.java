package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class DebugMain {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/google-cloud-java/java-translate/google-cloud-translate/src/main/java/com/google/cloud/translate/spi/v2/HttpTranslateRpc.java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        launcher.addProcessor(new AbstractProcessor<CtClass<?>>() {
            @Override
            public void process(CtClass<?> ctClass) {
                System.out.println("Processing class: " + ctClass.getQualifiedName());
                ctClass.getElements(new TypeFilter<CtImport>(CtImport.class))
                    .forEach(imp -> {
                        System.out.println("Import: " + imp);
                        CtReference ref = imp.getReference();
                        System.out.println("  Reference type: " + ref.getClass());
                        if (ref instanceof CtTypeReference) {
                            CtTypeReference<?> typeRef = (CtTypeReference<?>) ref;
                            System.out.println("  Type ref: " + typeRef.getQualifiedName());
                        }
                    });
            }
        });
        
        launcher.run();
    }
}
