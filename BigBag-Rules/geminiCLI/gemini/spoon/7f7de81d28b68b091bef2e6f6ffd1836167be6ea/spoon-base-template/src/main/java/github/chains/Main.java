package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        Launcher spoon = new Launcher();
        spoon.addInputResource("/workspace/snmpman/snmpman/src/main/java/com/oneandone/snmpman/SnmpmanAgent.java");
        spoon.getEnvironment().setAutoImports(false);
        spoon.getEnvironment().setNoClasspath(true);
        spoon.getEnvironment().setCommentEnabled(true);
        spoon.getEnvironment().setPreserveLineNumbers(true); // Try to preserve formatting
        
        spoon.buildModel();
        
        List<CtLocalVariable> vars = spoon.getModel().getElements(new TypeFilter<>(CtLocalVariable.class));
        boolean found = false;
        
        for (CtLocalVariable var : vars) {
            if ("reg".equals(var.getSimpleName())) {
                // Check ancestors to confirm it is in registerHard
                 CtMethod method = var.getParent(CtMethod.class);
                 if (method != null && "registerHard".equals(method.getSimpleName())) {
                    System.out.println("Found variable 'reg' in 'registerHard'. Applying fix...");
                    
                    // Create the replacement expression with double cast
                    // Casting to raw SortedMap first, then to the parameterized SortedMap
                    String newCode = "(java.util.SortedMap<org.snmp4j.agent.MOScope, org.snmp4j.agent.ManagedObject>) (java.util.SortedMap) server.getRegistry()";
                    CtExpression newInit = spoon.getFactory().Code().createCodeSnippetExpression(newCode);
                    var.setDefaultExpression(newInit);
                    found = true;
                 }
            }
        }
        
        
        // Fix Collections usage
        List<spoon.reflect.code.CtTypeAccess> typeAccesses = spoon.getModel().getElements(new TypeFilter<>(spoon.reflect.code.CtTypeAccess.class));
        for (spoon.reflect.code.CtTypeAccess access : typeAccesses) {
            if ("Collections".equals(access.getAccessedType().getSimpleName())) {
                spoon.reflect.reference.CtTypeReference<?> ref = access.getAccessedType();
                if (ref.getPackage() == null || ref.getPackage().getSimpleName().isEmpty()) {
                     ref.setPackage(spoon.getFactory().Package().getOrCreate("java.util").getReference());
                }
            }
        }

        
        // Fix Variable usage
        List<spoon.reflect.reference.CtTypeReference> typeRefs = spoon.getModel().getElements(new TypeFilter<>(spoon.reflect.reference.CtTypeReference.class));
        for (spoon.reflect.reference.CtTypeReference ref : typeRefs) {
            if ("Variable".equals(ref.getSimpleName())) {
                 if (ref.getPackage() == null || ref.getPackage().getSimpleName().isEmpty() || "snmpman".equals(ref.getPackage().getSimpleName()) || "com.oneandone.snmpman".equals(ref.getPackage().getQualifiedName())) {
                     ref.setPackage(spoon.getFactory().Package().getOrCreate("org.snmp4j.smi").getReference());
                 }
            }
        }

        if (found) {
            spoon.setSourceOutputDirectory(new File("/workspace/spoon-output"));
            spoon.prettyprint();
            System.out.println("Transformation applied. Output saved to /workspace/spoon-output");
        } else {
            System.err.println("Could not find the target variable to transform.");
            System.exit(1);
        }
    }
}
