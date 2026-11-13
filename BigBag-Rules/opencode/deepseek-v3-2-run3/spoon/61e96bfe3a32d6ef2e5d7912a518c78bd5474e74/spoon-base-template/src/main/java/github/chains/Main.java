package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.code.CtReturn;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformation.jar <input-source-dir> <output-source-dir>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src /path/to/transformed/src");
            System.exit(1);
        }

        String inputSourceDir = args[0];
        String outputSourceDir = args[1];

        System.out.println("Applying logback-core 1.2.9 Encoder API transformation...");
        System.out.println("Input directory: " + inputSourceDir);
        System.out.println("Output directory: " + outputSourceDir);

        try {
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(false);
            launcher.addInputResource(inputSourceDir);
            launcher.setSourceOutputDirectory(outputSourceDir);

            // Create the processor to fix EncoderBase subclasses
            launcher.addProcessor(new LogbackEncoderApiTransformer());
            
            launcher.run();
            
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

class LogbackEncoderApiTransformer extends spoon.processing.AbstractProcessor<CtClass<?>> {
    
    @Override
    public void process(CtClass<?> ctClass) {
        // Check if this class extends EncoderBase by checking superclass name
        if (!extendsEncoderBase(ctClass)) {
            return;
        }
        
        System.out.println("Processing EncoderBase subclass: " + ctClass.getQualifiedName());
        
        // Get the type parameter E from EncoderBase<E>
        CtTypeReference<?> elementType = getEncoderElementType(ctClass);
        if (elementType == null) {
            System.err.println("Warning: Could not determine element type for " + ctClass.getQualifiedName());
            return;
        }
        
        // Transform the class
        transformEncoderClass(ctClass, elementType);
    }
    
    private boolean extendsEncoderBase(CtClass<?> ctClass) {
        CtTypeReference<?> superClass = ctClass.getSuperclass();
        if (superClass == null) {
            return false;
        }
        
        // Simple check: does the superclass name contain EncoderBase?
        String superClassName = superClass.getQualifiedName();
        return superClassName != null && superClassName.contains("EncoderBase");
    }
    
    private CtTypeReference<?> getEncoderElementType(CtClass<?> ctClass) {
        // Get the type parameter from EncoderBase<E>
        CtTypeReference<?> superClass = ctClass.getSuperclass();
        if (superClass == null || superClass.getActualTypeArguments().isEmpty()) {
            return null;
        }
        
        return superClass.getActualTypeArguments().get(0);
    }
    
    private void transformEncoderClass(CtClass<?> ctClass, CtTypeReference<?> elementType) {
        // 1. Check for doEncode(E) method and rename it to encode(E)
        renameDoEncodeToEncode(ctClass, elementType);
        
        // 2. Add encode(E) method if not present
        addEncodeMethodIfMissing(ctClass, elementType);
        
        // 3. Add headerBytes() method if not present
        addHeaderBytesMethod(ctClass);
        
        // 4. Add footerBytes() method if not present
        addFooterBytesMethod(ctClass);
    }
    
    private void renameDoEncodeToEncode(CtClass<?> ctClass, CtTypeReference<?> elementType) {
        List<CtMethod<?>> doEncodeMethods = ctClass.getElements(new TypeFilter<CtMethod<?>>(CtMethod.class) {
            @Override
            public boolean matches(CtMethod<?> method) {
                return method.getSimpleName().equals("doEncode") && 
                       method.getParameters().size() == 1;
            }
        });
        
        for (CtMethod<?> doEncodeMethod : doEncodeMethods) {
            System.out.println("  - Renaming doEncode(E) to encode(E)");
            
            // Change method name to encode
            doEncodeMethod.setSimpleName("encode");
            
            // Change return type to byte[]
            CtTypeReference<?> byteArrayRef = getFactory().Type().createArrayReference(
                getFactory().Type().createReference(byte.class)
            );
            doEncodeMethod.setType(byteArrayRef);
            
            // Get the old method body
            CtBlock<?> oldBody = doEncodeMethod.getBody();
            if (oldBody != null) {
                // Create new body that executes old logic and returns empty byte array
                CtBlock<?> newBody = getFactory().createBlock();
                
                // Copy all statements from old body
                for (spoon.reflect.code.CtStatement stmt : oldBody.getStatements()) {
                    newBody.addStatement(stmt.clone());
                }
                
                // Add return statement with empty byte array
                CtCodeSnippetStatement returnStmt = getFactory().createCodeSnippetStatement("return new byte[0]");
                newBody.addStatement(returnStmt);
                
                doEncodeMethod.setBody(newBody);
            }
        }
    }
    
    private void addEncodeMethodIfMissing(CtClass<?> ctClass, CtTypeReference<?> elementType) {
        List<CtMethod<?>> encodeMethods = ctClass.getElements(new TypeFilter<CtMethod<?>>(CtMethod.class) {
            @Override
            public boolean matches(CtMethod<?> method) {
                return method.getSimpleName().equals("encode") && 
                       method.getParameters().size() == 1;
            }
        });
        
        if (encodeMethods.isEmpty()) {
            System.out.println("  - Adding encode(E) method");
            
            // Create method using code snippet to avoid type issues
            CtMethod<?> encodeMethod = getFactory().createMethod();
            encodeMethod.setSimpleName("encode");
            encodeMethod.addModifier(ModifierKind.PUBLIC);
            
            // Create byte[] return type
            CtTypeReference<?> byteArrayRef = getFactory().Type().createArrayReference(
                getFactory().Type().createReference(byte.class)
            );
            encodeMethod.setType(byteArrayRef);
            
            // Add parameter
            encodeMethod.addParameter(getFactory().createParameter(
                encodeMethod, 
                elementType.clone(), 
                "event"
            ));
            
            // Add method body using code snippet
            CtBlock<?> body = getFactory().createBlock();
            CtCodeSnippetStatement returnStmt = getFactory().createCodeSnippetStatement("return new byte[0]");
            body.addStatement(returnStmt);
            encodeMethod.setBody(body);
            
            ctClass.addMethod(encodeMethod);
        }
    }
    
    private void addHeaderBytesMethod(CtClass<?> ctClass) {
        List<CtMethod<?>> headerBytesMethods = ctClass.getElements(new TypeFilter<CtMethod<?>>(CtMethod.class) {
            @Override
            public boolean matches(CtMethod<?> method) {
                return method.getSimpleName().equals("headerBytes");
            }
        });
        
        if (headerBytesMethods.isEmpty()) {
            System.out.println("  - Adding headerBytes() method");
            addBytesMethod(ctClass, "headerBytes");
        }
    }
    
    private void addFooterBytesMethod(CtClass<?> ctClass) {
        List<CtMethod<?>> footerBytesMethods = ctClass.getElements(new TypeFilter<CtMethod<?>>(CtMethod.class) {
            @Override
            public boolean matches(CtMethod<?> method) {
                return method.getSimpleName().equals("footerBytes");
            }
        });
        
        if (footerBytesMethods.isEmpty()) {
            System.out.println("  - Adding footerBytes() method");
            addBytesMethod(ctClass, "footerBytes");
        }
    }
    
    private void addBytesMethod(CtClass<?> ctClass, String methodName) {
        CtMethod<?> method = getFactory().createMethod();
        method.setSimpleName(methodName);
        method.addModifier(ModifierKind.PUBLIC);
        
        // Create byte[] return type
        CtTypeReference<?> byteArrayRef = getFactory().Type().createArrayReference(
            getFactory().Type().createReference(byte.class)
        );
        method.setType(byteArrayRef);
        
        // Add method body that returns null
        CtBlock<?> body = getFactory().createBlock();
        CtCodeSnippetStatement returnStmt = getFactory().createCodeSnippetStatement("return null");
        body.addStatement(returnStmt);
        method.setBody(body);
        
        ctClass.addMethod(method);
    }
    
    @Override
    public boolean isToBeProcessed(CtClass<?> candidate) {
        return extendsEncoderBase(candidate);
    }
}