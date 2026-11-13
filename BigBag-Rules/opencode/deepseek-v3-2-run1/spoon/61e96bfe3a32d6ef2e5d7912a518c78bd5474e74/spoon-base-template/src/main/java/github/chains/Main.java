package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.Set;
import java.util.HashSet;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setSourceClasspath(new String[0]);
        // Exclude target directories
        launcher.getEnvironment().setNoClasspath(false);
        
        CtModel model = launcher.buildModel();
        
        // Find all classes extending EncoderBase
        Set<CtClass<?>> encoderBaseSubclasses = new HashSet<>();
        model.getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> ctClass) {
                if (ctClass.isAnonymous() || ctClass.isInterface()) {
                    return false;
                }
                
                // Check if this class extends EncoderBase
                CtTypeReference<?> superclass = ctClass.getSuperclass();
                while (superclass != null) {
                    if ("ch.qos.logback.core.encoder.EncoderBase".equals(superclass.getQualifiedName())) {
                        return true;
                    }
                    // Check if superclass extends EncoderBase through inheritance chain
                    try {
                        superclass = superclass.getSuperclass();
                    } catch (Exception e) {
                        break;
                    }
                }
                return false;
            }
        }).forEach(encoderBaseSubclasses::add);
        
        System.out.println("Found " + encoderBaseSubclasses.size() + " classes extending EncoderBase");
        
        for (CtClass<?> clazz : encoderBaseSubclasses) {
            System.out.println("Processing class: " + clazz.getQualifiedName());
            
            // Check and add required Encoder interface methods if missing
            boolean hasEncode = false;
            boolean hasFooterBytes = false;
            boolean hasHeaderBytes = false;
            
            for (CtMethod<?> method : clazz.getMethods()) {
                String methodName = method.getSimpleName();
                if ("encode".equals(methodName) && 
                    method.getParameters().size() == 1) {
                    // Check if return type is byte[]
                    String returnType = method.getType().toString();
                    if ("byte[]".equals(returnType)) {
                        hasEncode = true;
                    }
                } else if ("footerBytes".equals(methodName) && 
                    method.getParameters().isEmpty()) {
                    // Check if return type is byte[]
                    String returnType = method.getType().toString();
                    if ("byte[]".equals(returnType)) {
                        hasFooterBytes = true;
                    }
                } else if ("headerBytes".equals(methodName) && 
                    method.getParameters().isEmpty()) {
                    // Check if return type is byte[]
                    String returnType = method.getType().toString();
                    if ("byte[]".equals(returnType)) {
                        hasHeaderBytes = true;
                    }
                }
            }
            
            // Remove @Override from methods that don't override Encoder interface methods
            for (CtMethod<?> method : clazz.getMethods()) {
                String methodName = method.getSimpleName();
                // These methods shouldn't have @Override in new API
                if ("init".equals(methodName) || "doEncode".equals(methodName) || "close".equals(methodName)) {
                    // Check if method has @Override annotation and remove it
                    if (method.getAnnotations().stream().anyMatch(a -> a.getAnnotationType().getSimpleName().equals("Override"))) {
                        method.getAnnotations().removeIf(a -> a.getAnnotationType().getSimpleName().equals("Override"));
                    }
                }
            }
            
            // Add encode() method if missing
            if (!hasEncode) {
                System.out.println("  Adding encode() method signature with TODO implementation");
                // Create method with CodeSnippetStatement
                try {
                    CtMethod<byte[]> encodeMethod = clazz.getFactory().createMethod();
                    encodeMethod.setSimpleName("encode");
                    encodeMethod.setType(clazz.getFactory().Type().createArrayReference(clazz.getFactory().Type().BYTE));
                    // Add a generic Object parameter - actual type should match EncoderBase<E>
                    // First create the method, then add parameter
                    encodeMethod.setBody(clazz.getFactory().createCodeSnippetStatement(
                        "// TODO: Implement encode() method for " + clazz.getSimpleName() + "\n" +
                        "// This method must convert the argument to byte[]\n" +
                        "// The argument type should match EncoderBase's type parameter\n" +
                        "throw new UnsupportedOperationException(\"encode() not implemented\");"
                    ));
                    encodeMethod.addModifier(ModifierKind.PUBLIC);
                    clazz.addMethod(encodeMethod);
                    
                    // Add parameter after method is added to class
                    CtTypeReference<Object> objectType = clazz.getFactory().Type().OBJECT;
                    CtParameter<Object> param = clazz.getFactory().createParameter(encodeMethod, objectType, "arg0");
                    encodeMethod.addParameter(param);
                } catch (Exception e) {
                    System.err.println("  Could not add encode() method: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            // Add footerBytes() method if missing
            if (!hasFooterBytes) {
                System.out.println("  Adding footerBytes() method");
                CtMethod<byte[]> footerMethod = clazz.getFactory().createMethod();
                footerMethod.setSimpleName("footerBytes");
                footerMethod.setType(clazz.getFactory().Type().createArrayReference(clazz.getFactory().Type().BYTE));
                footerMethod.setBody(clazz.getFactory().createCodeSnippetStatement("return new byte[0]"));
                footerMethod.addModifier(ModifierKind.PUBLIC);
                clazz.addMethod(footerMethod);
            }
            
            // Add headerBytes() method if missing
            if (!hasHeaderBytes) {
                System.out.println("  Adding headerBytes() method");
                CtMethod<byte[]> headerMethod = clazz.getFactory().createMethod();
                headerMethod.setSimpleName("headerBytes");
                headerMethod.setType(clazz.getFactory().Type().createArrayReference(clazz.getFactory().Type().BYTE));
                headerMethod.setBody(clazz.getFactory().createCodeSnippetStatement("return new byte[0]"));
                headerMethod.addModifier(ModifierKind.PUBLIC);
                clazz.addMethod(headerMethod);
            }
        }
        
        // Apply transformations - write to same directory
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
    }
}