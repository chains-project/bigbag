package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * A Spoon transformation that fixes compilation errors when upgrading
 * from older logback-core versions to 1.2.9+.
 * 
 * Breaking Change Summary:
 * - Old API: EncoderBase had abstract methods: init(OutputStream), doEncode(E), close()
 * - New API: EncoderBase has abstract methods: encode(E), headerBytes(), footerBytes()
 * 
 * Transformation steps:
 * 1. Rename doEncode(E) to encode(E) and change return type to byte[]
 * 2. Add headerBytes() method returning null
 * 3. Add footerBytes() method returning null  
 * 4. Remove @Override from init(OutputStream) and close() methods
 */
public class LogbackEncoderFix {
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -cp spoon-transformation.jar github.chains.LogbackEncoderFix <input-source-dir> <output-source-dir>");
            System.err.println("Example: java -cp spoon-transformation.jar github.chains.LogbackEncoderFix /path/to/project/src /path/to/transformed/src");
            System.exit(1);
        }
        
        String inputSourceDir = args[0];
        String outputSourceDir = args[1];
        
        System.out.println("Applying logback-core 1.2.9 Encoder API transformation...");
        System.out.println("Fixes: doEncode→encode, adds headerBytes/footerBytes, removes invalid @Override");
        System.out.println("Input directory: " + inputSourceDir);
        System.out.println("Output directory: " + outputSourceDir);
        
        try {
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(false);
            launcher.addInputResource(inputSourceDir);
            launcher.setSourceOutputDirectory(outputSourceDir);
            
            launcher.addProcessor(new FixEncoderBaseProcessor());
            launcher.run();
            
            System.out.println("Transformation completed successfully!");
            System.out.println("Summary of changes applied to EncoderBase subclasses:");
            System.out.println("  1. Renamed doEncode(E) to encode(E) with byte[] return");
            System.out.println("  2. Added headerBytes() returning null");
            System.out.println("  3. Added footerBytes() returning null");
            System.out.println("  4. Removed @Override from init(OutputStream) and close()");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

class FixEncoderBaseProcessor extends spoon.processing.AbstractProcessor<CtClass<?>> {
    
    @Override
    public void process(CtClass<?> ctClass) {
        if (!extendsEncoderBase(ctClass)) {
            return;
        }
        
        System.out.println("Processing: " + ctClass.getQualifiedName());
        
        CtTypeReference<?> elementType = getEncoderElementType(ctClass);
        if (elementType == null) {
            elementType = getFactory().Type().OBJECT;
        }
        
        applyFixes(ctClass, elementType);
    }
    
    private boolean extendsEncoderBase(CtClass<?> ctClass) {
        CtTypeReference<?> superClass = ctClass.getSuperclass();
        if (superClass == null) return false;
        
        String superName = superClass.getQualifiedName();
        return superName != null && 
               (superName.equals("ch.qos.logback.core.encoder.EncoderBase") ||
                superName.endsWith(".EncoderBase"));
    }
    
    private CtTypeReference<?> getEncoderElementType(CtClass<?> ctClass) {
        CtTypeReference<?> superClass = ctClass.getSuperclass();
        if (superClass == null || superClass.getActualTypeArguments().isEmpty()) {
            return null;
        }
        return superClass.getActualTypeArguments().get(0);
    }
    
    private void applyFixes(CtClass<?> ctClass, CtTypeReference<?> elementType) {
        // 1. Fix encode method
        fixEncodeMethod(ctClass, elementType);
        
        // 2. Add missing required methods
        addHeaderBytesMethod(ctClass);
        addFooterBytesMethod(ctClass);
        
        // 3. Remove invalid @Override annotations
        removeInvalidOverrides(ctClass);
    }
    
    private void fixEncodeMethod(CtClass<?> ctClass, CtTypeReference<?> elementType) {
        // Find doEncode method
        List<CtMethod<?>> doEncodeMethods = ctClass.getElements(new TypeFilter<CtMethod<?>>(CtMethod.class) {
            @Override
            public boolean matches(CtMethod<?> method) {
                return "doEncode".equals(method.getSimpleName()) && 
                       method.getParameters().size() == 1;
            }
        });
        
        if (!doEncodeMethods.isEmpty()) {
            for (CtMethod<?> doEncodeMethod : doEncodeMethods) {
                System.out.println("  - Renaming doEncode to encode");
                
                // Rename
                doEncodeMethod.setSimpleName("encode");
                
                // Change return type to byte[]
                CtTypeReference<?> byteArrayType = createByteArrayType();
                doEncodeMethod.setType(byteArrayType);
                
                // Add return statement if not present
                CtBlock<?> body = doEncodeMethod.getBody();
                if (body != null) {
                    // Check if body already has return statement
                    boolean hasReturn = body.toString().contains("return");
                    if (!hasReturn) {
                        CtCodeSnippetStatement returnStmt = getFactory().createCodeSnippetStatement(
                            "return new byte[0];"
                        );
                        body.addStatement(returnStmt);
                    }
                }
                
                // Add @Override annotation
                addOverrideAnnotation(doEncodeMethod);
            }
        } else {
            // Check if encode already exists
            List<CtMethod<?>> encodeMethods = ctClass.getElements(new TypeFilter<CtMethod<?>>(CtMethod.class) {
                @Override
                public boolean matches(CtMethod<?> method) {
                    return "encode".equals(method.getSimpleName()) && 
                           method.getParameters().size() == 1;
                }
            });
            
            if (encodeMethods.isEmpty()) {
                System.out.println("  - Adding encode method");
                addNewEncodeMethod(ctClass, elementType);
            }
        }
    }
    
    private void addNewEncodeMethod(CtClass<?> ctClass, CtTypeReference<?> elementType) {
        CtMethod<?> method = getFactory().createMethod();
        method.setSimpleName("encode");
        method.addModifier(ModifierKind.PUBLIC);
        
        method.setType(createByteArrayType());
        method.addParameter(getFactory().createParameter(method, elementType.clone(), "event"));
        
        CtBlock<?> body = getFactory().createBlock();
        body.addStatement(getFactory().createCodeSnippetStatement(
            "// TODO: Implement encode method to return actual bytes\n" +
            "return new byte[0];"
        ));
        method.setBody(body);
        
        addOverrideAnnotation(method);
        ctClass.addMethod(method);
    }
    
    private void addHeaderBytesMethod(CtClass<?> ctClass) {
        if (hasMethod(ctClass, "headerBytes")) return;
        
        System.out.println("  - Adding headerBytes method");
        addBytesMethod(ctClass, "headerBytes");
    }
    
    private void addFooterBytesMethod(CtClass<?> ctClass) {
        if (hasMethod(ctClass, "footerBytes")) return;
        
        System.out.println("  - Adding footerBytes method");
        addBytesMethod(ctClass, "footerBytes");
    }
    
    private boolean hasMethod(CtClass<?> ctClass, String methodName) {
        return !ctClass.getElements(new TypeFilter<CtMethod<?>>(CtMethod.class) {
            @Override
            public boolean matches(CtMethod<?> method) {
                return methodName.equals(method.getSimpleName());
            }
        }).isEmpty();
    }
    
    private void addBytesMethod(CtClass<?> ctClass, String methodName) {
        CtMethod<?> method = getFactory().createMethod();
        method.setSimpleName(methodName);
        method.addModifier(ModifierKind.PUBLIC);
        method.setType(createByteArrayType());
        
        CtBlock<?> body = getFactory().createBlock();
        body.addStatement(getFactory().createCodeSnippetStatement("return null;"));
        method.setBody(body);
        
        addOverrideAnnotation(method);
        ctClass.addMethod(method);
    }
    
    private void removeInvalidOverrides(CtClass<?> ctClass) {
        // Remove @Override from init(OutputStream) and close() methods
        List<CtMethod<?>> methods = ctClass.getMethods();
        for (CtMethod<?> method : methods) {
            String name = method.getSimpleName();
            if (("init".equals(name) && hasOutputStreamParam(method)) || 
                "close".equals(name)) {
                
                // Remove @Override annotation if present
                CtAnnotation<?> overrideAnnotation = method.getAnnotation(Override.class);
                if (overrideAnnotation != null) {
                    System.out.println("  - Removing @Override from " + name + " method");
                    method.removeAnnotation(overrideAnnotation);
                }
            }
        }
    }
    
    private boolean hasOutputStreamParam(CtMethod<?> method) {
        return method.getParameters().size() == 1 &&
               method.getParameters().get(0).getType().getQualifiedName().equals("java.io.OutputStream");
    }
    
    private CtTypeReference<?> createByteArrayType() {
        return getFactory().Type().createArrayReference(
            getFactory().Type().createReference(byte.class)
        );
    }
    
    private void addOverrideAnnotation(CtMethod<?> method) {
        try {
            method.addAnnotation(getFactory().createAnnotation(
                getFactory().Type().createReference(Override.class)
            ));
        } catch (Exception e) {
            // Ignore if annotation can't be added
        }
    }
    
    @Override
    public boolean isToBeProcessed(CtClass<?> candidate) {
        return extendsEncoderBase(candidate);
    }
}