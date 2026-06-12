package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Add the specific file
        launcher.addInputResource("/workspace/snmpman/snmpman/src/main/java/com/oneandone/snmpman/SnmpmanAgent.java");
        
        // Build the model
        launcher.buildModel();
        CtModel model = launcher.getModel();

        // Find the SnmpmanAgent class
        CtType<?> snmpmanAgentClass = model.getAllTypes().stream()
                .filter(t -> t.getSimpleName().equals("SnmpmanAgent"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("SnmpmanAgent class not found"));

        // 1. Fix the compilation error: registerHard method
        CtMethod<?> registerHardMethod = snmpmanAgentClass.getMethodsByName("registerHard").stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("registerHard method not found"));

        List<CtLocalVariable> localVariables = registerHardMethod.getElements(new TypeFilter<>(CtLocalVariable.class));
        CtLocalVariable regVar = localVariables.stream()
                .filter(v -> v.getSimpleName().equals("reg"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Variable 'reg' not found"));

        // Change the type: SortedMap<MOScope, ManagedObject> -> SortedMap<MOScope, ManagedObject<?>>
        CtTypeReference<?> sortedMapRef = launcher.getFactory().Type().createReference("java.util.SortedMap");
        CtTypeReference<?> moScopeRef = launcher.getFactory().Type().createReference("org.snmp4j.agent.MOScope");
        CtTypeReference<?> managedObjectRef = launcher.getFactory().Type().createReference("org.snmp4j.agent.ManagedObject");
        
        CtTypeReference<?> wildcardRef = launcher.getFactory().createWildcardReference();
        managedObjectRef.addActualTypeArgument(wildcardRef);

        sortedMapRef.addActualTypeArgument(moScopeRef);
        sortedMapRef.addActualTypeArgument(managedObjectRef);

        regVar.setType(sortedMapRef);

        // 2. Fix potential import issues due to noclasspath
        // Force 'Variable' to be 'org.snmp4j.smi.Variable'
        model.getElements(new TypeFilter<>(CtTypeReference.class)).stream()
                .filter(ref -> "Variable".equals(ref.getSimpleName()) && ref.getPackage() == null)
                .forEach(ref -> ref.setPackage(launcher.getFactory().Package().createReference("org.snmp4j.smi")));
        
        // Force 'Collections' to be 'java.util.Collections'
        model.getElements(new TypeFilter<>(CtTypeReference.class)).stream()
                .filter(ref -> "Collections".equals(ref.getSimpleName()) && ref.getPackage() == null)
                .forEach(ref -> ref.setPackage(launcher.getFactory().Package().createReference("java.util")));

        // Overwrite the original file
        launcher.setSourceOutputDirectory("/workspace/snmpman/snmpman/src/main/java");
        launcher.prettyprint();
        
        System.out.println("Transformation applied successfully.");
    }
}