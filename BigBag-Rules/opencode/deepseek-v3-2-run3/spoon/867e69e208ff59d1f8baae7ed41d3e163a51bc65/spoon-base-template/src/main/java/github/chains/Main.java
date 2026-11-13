package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.replace.ReplacementVisitor;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir + "/src/main/java");
        launcher.addInputResource(sourceDir + "/src/test/java");
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Count transformations
        int importTransforms = 0;
        int typeRefTransforms = 0;
        int constructorTransforms = 0;
        
        // Transform 1: Update import statements for TFramedTransport and TFastFramedTransport
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport imp : imports) {
            String importStr = imp.toString();
            if (importStr.contains("org.apache.thrift.transport.TFramedTransport") && 
                !importStr.contains("org.apache.thrift.transport.layered.TFramedTransport")) {
                // Replace the import
                imp.replace(launcher.getFactory().createImport(
                    launcher.getFactory().createReference("org.apache.thrift.transport.layered.TFramedTransport")
                ));
                importTransforms++;
            }
            if (importStr.contains("org.apache.thrift.transport.TFastFramedTransport") && 
                !importStr.contains("org.apache.thrift.transport.layered.TFastFramedTransport")) {
                // Replace the import
                imp.replace(launcher.getFactory().createImport(
                    launcher.getFactory().createReference("org.apache.thrift.transport.layered.TFastFramedTransport")
                ));
                importTransforms++;
            }
        }
        
        // Transform 2: Update type references in class declarations, fields, method parameters, etc.
        List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> typeRef : typeRefs) {
            String typeQualifiedName = typeRef.getQualifiedName();
            if (typeQualifiedName.equals("org.apache.thrift.transport.TFramedTransport")) {
                typeRef.replace(launcher.getFactory().createReference("org.apache.thrift.transport.layered.TFramedTransport"));
                typeRefTransforms++;
            } else if (typeQualifiedName.equals("org.apache.thrift.transport.TFastFramedTransport")) {
                typeRef.replace(launcher.getFactory().createReference("org.apache.thrift.transport.layered.TFastFramedTransport"));
                typeRefTransforms++;
            }
        }
        
        // Transform 3: Update constructor calls (these might use fully qualified class names)
        List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> constr : constructorCalls) {
            CtTypeReference<?> constrType = constr.getType();
            if (constrType != null) {
                String typeName = constrType.getQualifiedName();
                if (typeName.equals("org.apache.thrift.transport.TFramedTransport")) {
                    constrType.replace(launcher.getFactory().createReference("org.apache.thrift.transport.layered.TFramedTransport"));
                    constructorTransforms++;
                } else if (typeName.equals("org.apache.thrift.transport.TFastFramedTransport")) {
                    constrType.replace(launcher.getFactory().createReference("org.apache.thrift.transport.layered.TFastFramedTransport"));
                    constructorTransforms++;
                }
            }
        }
        
        // Output the transformed code
        System.out.println("Transformation summary:");
        System.out.println("  Import statements updated: " + importTransforms);
        System.out.println("  Type references updated: " + typeRefTransforms);
        System.out.println("  Constructor calls updated: " + constructorTransforms);
        
        // Write transformed files back
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully.");
    }
}