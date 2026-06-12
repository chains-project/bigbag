package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class FixStruts2 {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        String filePath = "/workspace/guice/extensions/struts2/test/com/google/inject/struts2/Struts2FactoryTest.java";
        launcher.addInputResource(filePath);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);

        launcher.buildModel();

        System.out.println("Types found: " + launcher.getModel().getAllTypes().size());

        for (CtType<?> type : launcher.getModel().getAllTypes()) {
            System.out.println("Processing type: " + type.getQualifiedName());
            
            for (CtTypeReference<?> ref : type.getElements(new TypeFilter<>(CtTypeReference.class))) {
                if ("org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter".equals(ref.getQualifiedName())) {
                    System.out.println("Updating reference: " + ref.getQualifiedName());
                    ref.setPackage(type.getFactory().Package().createReference("org.apache.struts2.dispatcher.filter"));
                }
            }
        }

        launcher.setSourceOutputDirectory("/workspace/guice/extensions/struts2/test");
        launcher.prettyprint();
    }
}
