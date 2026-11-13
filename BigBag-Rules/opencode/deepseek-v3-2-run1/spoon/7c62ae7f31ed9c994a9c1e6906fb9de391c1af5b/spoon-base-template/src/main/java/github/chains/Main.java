package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.ImportScannerImpl;
import spoon.reflect.declaration.CtImportKind;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Hibernate UserType API migration to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        // Process only Java files in the hibernate module
        launcher.addInputResource(sourceDir + "/onebusaway-gtfs-hibernate/src/main/java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setShouldCompile(false);
        
        // Apply the transformations - output to temp directory
        String tempDir = "/tmp/spoon-output-" + System.currentTimeMillis();
        launcher.setSourceOutputDirectory(tempDir);
        
        CtModel model = launcher.buildModel();
        
        // Find all classes implementing UserType or CompositeUserType
        List<CtClass<?>> userTypeClasses = model.getElements(new Filter<CtClass<?>>() {
            @Override
            public boolean matches(CtClass<?> element) {
                // Check if class implements org.hibernate.usertype.UserType or CompositeUserType
                for (CtTypeReference<?> interfaceRef : element.getSuperInterfaces()) {
                    String interfaceName = interfaceRef.getQualifiedName();
                    if (interfaceName.equals("org.hibernate.usertype.UserType") ||
                        interfaceName.equals("org.hibernate.usertype.CompositeUserType")) {
                        return true;
                    }
                }
                return false;
            }
        });
        
        System.out.println("Found " + userTypeClasses.size() + " classes implementing UserType or CompositeUserType");
        
        for (CtClass<?> userTypeClass : userTypeClasses) {
            System.out.println("Processing class: " + userTypeClass.getQualifiedName());
            
            // Fix nullSafeGet method
            fixNullSafeGetMethod(userTypeClass);
            
            // Fix nullSafeSet method  
            fixNullSafeSetMethod(userTypeClass);
            
            // For CompositeUserType, also fix assemble, disassemble, and replace methods
            fixCompositeUserTypeMethods(userTypeClass);
        }
        
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
        System.out.println("Transformed files written to: " + tempDir);
    }
    
    private static void fixNullSafeGetMethod(CtClass<?> userTypeClass) {
        fixMethodParameter(userTypeClass, "nullSafeGet", 2, "org.hibernate.engine.spi.SessionImplementor");
    }
    
    private static void fixNullSafeSetMethod(CtClass<?> userTypeClass) {
        fixMethodParameter(userTypeClass, "nullSafeSet", 3, "org.hibernate.engine.spi.SessionImplementor");
    }
    
    private static void fixCompositeUserTypeMethods(CtClass<?> userTypeClass) {
        // Check if this class implements CompositeUserType
        boolean isCompositeUserType = false;
        for (CtTypeReference<?> interfaceRef : userTypeClass.getSuperInterfaces()) {
            if (interfaceRef.getQualifiedName().equals("org.hibernate.usertype.CompositeUserType")) {
                isCompositeUserType = true;
                break;
            }
        }
        
        if (!isCompositeUserType) {
            return;
        }
        
        // Fix assemble method for CompositeUserType
        fixMethodParameter(userTypeClass, "assemble", 1, "org.hibernate.engine.spi.SessionImplementor");
        
        // Fix disassemble method for CompositeUserType
        fixMethodParameter(userTypeClass, "disassemble", 1, "org.hibernate.engine.spi.SessionImplementor");
        
        // Fix replace method for CompositeUserType
        fixMethodParameter(userTypeClass, "replace", 2, "org.hibernate.engine.spi.SessionImplementor");
    }
    
    private static void fixMethodParameter(CtClass<?> userTypeClass, String methodName, int paramIndex, String oldTypeName) {
        for (CtMethod<?> method : userTypeClass.getMethods()) {
            if (method.getSimpleName().equals(methodName)) {
                List<CtParameter<?>> parameters = method.getParameters();
                if (parameters.size() > paramIndex) {
                    CtParameter<?> targetParam = parameters.get(paramIndex);
                    if (targetParam.getType().getQualifiedName().equals(oldTypeName)) {
                        System.out.println("  Fixing " + methodName + " method signature");
                        
                        // Change the parameter type from SessionImplementor to SharedSessionContractImplementor
                        CtTypeReference<?> newType = userTypeClass.getFactory().Type().createReference(
                            "org.hibernate.engine.spi.SharedSessionContractImplementor");
                        targetParam.setType(newType);
                    }
                }
            }
        }
    }
}