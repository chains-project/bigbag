package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/onebusaway-gtfs-modules/onebusaway-gtfs-hibernate/src/main/java/org/onebusaway/gtfs/impl/ServiceDateUserType.java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        CtModel model = launcher.buildModel();
        
        CtTypeReference<?> newTypeRef = launcher.getFactory().Type().createReference("org.hibernate.engine.spi.SharedSessionContractImplementor");
        
        for (CtClass<?> ctClass : model.getElements(new TypeFilter<>(CtClass.class))) {
            if (ctClass.getQualifiedName().equals("org.onebusaway.gtfs.impl.ServiceDateUserType")) {
                for (CtMethod<?> method : ctClass.getMethods()) {
                    if (method.getSimpleName().equals("nullSafeGet") || method.getSimpleName().equals("nullSafeSet")) {
                        for (CtParameter<?> parameter : method.getParameters()) {
                            if (parameter.getType().getSimpleName().equals("SessionImplementor")) {
                                parameter.setType((CtTypeReference) newTypeRef);
                            }
                        }
                    }
                }
            }
        }
        
        launcher.setSourceOutputDirectory("/workspace/onebusaway-gtfs-modules/onebusaway-gtfs-hibernate/src/main/java/");
        launcher.prettyprint();
    }
}
