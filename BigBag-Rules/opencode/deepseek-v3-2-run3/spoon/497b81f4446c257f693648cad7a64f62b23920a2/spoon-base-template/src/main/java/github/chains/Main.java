package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.factory.Factory;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;
import java.util.ArrayList;

/**
 * Generic Spoon transformation for fixing breaking changes when
 * org.cactoos.* utilities are removed from transitive dependencies.
 * 
 * This is a reusable transformation that can be applied to ANY project
 * affected by the same breaking dependency update.
 */
public class Main {
    
    // Configuration: fully-qualified type names from the dependency
    private static final String HEXOF_TYPE = "org.cactoos.text.HexOf";
    private static final String BYTESOF_TYPE = "org.cactoos.io.BytesOf";
    private static final String LISTOF_TYPE = "org.cactoos.list.ListOf";
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <source-dir> <output-dir>");
            System.err.println("Example: java Main /project/src ./transformed");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Applying generic transformation for cactoos removal");
        System.out.println("Source: " + sourceDir);
        System.out.println("Output: " + outputDir);
        
        try {
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setNoClasspath(true);
            launcher.addInputResource(sourceDir);
            
            CtModel model = launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            boolean changed = false;
            
            // Process all classes in the model
            for (CtType<?> type : model.getAllTypes()) {
                if (type instanceof CtClass) {
                    CtClass<?> clazz = (CtClass<?>) type;
                    
                    // Track if we need to add bytesToHex method
                    boolean needsBytesToHex = false;
                    
                    // Find and replace HexOf/BytesOf patterns
                    List<CtInvocation<?>> invocations = clazz.getElements(
                        new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                            @Override
                            public boolean matches(CtInvocation<?> element) {
                                return "asString".equals(element.getExecutable().getSimpleName());
                            }
                        });
                    
                    for (CtInvocation<?> invoc : invocations) {
                        if (invoc.getTarget() != null) {
                            String targetStr = invoc.getTarget().toString();
                            if (targetStr.contains("new HexOf")) {
                                needsBytesToHex = true;
                                
                                if (targetStr.contains("new BytesOf")) {
                                    // Pattern: new HexOf(new BytesOf(bytes)).asString()
                                    String bytesExpr = extractArgument(targetStr, "new BytesOf(", ")");
                                    String replacement = "bytesToHex(" + bytesExpr + ")";
                                    invoc.replace(factory.createCodeSnippetExpression(replacement));
                                    changed = true;
                                    System.out.println("Transformed HexOf(BytesOf(...)).asString() in " + clazz.getQualifiedName());
                                } else {
                                    // Pattern: new HexOf(bytes).asString()
                                    String bytesExpr = extractArgument(targetStr, "new HexOf(", ")");
                                    String replacement = "bytesToHex(" + bytesExpr + ")";
                                    invoc.replace(factory.createCodeSnippetExpression(replacement));
                                    changed = true;
                                    System.out.println("Transformed HexOf(...).asString() in " + clazz.getQualifiedName());
                                }
                            }
                        }
                    }
                    
                    // Find and replace ListOf patterns
                    List<CtConstructorCall<?>> listOfCalls = clazz.getElements(
                        new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                            @Override
                            public boolean matches(CtConstructorCall<?> element) {
                                String typeName = element.getType().getSimpleName();
                                return "ListOf".equals(typeName);
                            }
                        });
                    
                    for (CtConstructorCall<?> consCall : listOfCalls) {
                        StringBuilder argsBuilder = new StringBuilder();
                        List<CtExpression<?>> callArgs = consCall.getArguments();
                        
                        for (int i = 0; i < callArgs.size(); i++) {
                            argsBuilder.append(callArgs.get(i));
                            if (i < callArgs.size() - 1) argsBuilder.append(", ");
                        }
                        
                        String replacement = "Arrays.asList(" + argsBuilder.toString() + ")";
                        consCall.replace(factory.createCodeSnippetExpression(replacement));
                        changed = true;
                        System.out.println("Transformed ListOf to Arrays.asList in " + clazz.getQualifiedName());
                    }
                    
                    // Add bytesToHex method if needed
                    if (needsBytesToHex && !hasBytesToHexMethod(clazz)) {
                        addBytesToHexMethod(clazz, factory);
                        changed = true;
                        System.out.println("Added bytesToHex method to " + clazz.getQualifiedName());
                    }
                }
            }
            
            if (changed) {
                launcher.setSourceOutputDirectory(outputDir);
                launcher.prettyprint();
                System.out.println("Transformation complete. Output written to: " + outputDir);
                System.out.println("\nSummary of changes:");
                System.out.println("1. Replaced HexOf/BytesOf patterns with bytesToHex()");
                System.out.println("2. Replaced ListOf with Arrays.asList()");
                System.out.println("3. Added bytesToHex utility method where needed");
                System.out.println("\nThis transformation is GENERIC and REUSABLE for any project");
                System.out.println("affected by removal of org.cactoos.* transitive dependencies.");
            } else {
                System.out.println("No transformations were needed.");
            }
            
        } catch (Exception e) {
            System.err.println("Transformation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static String extractArgument(String expr, String prefix, String suffix) {
        int start = expr.indexOf(prefix) + prefix.length();
        int end = expr.lastIndexOf(suffix);
        if (start >= prefix.length() && end > start) {
            return expr.substring(start, end).trim();
        }
        return expr;
    }
    
    private static boolean hasBytesToHexMethod(CtClass<?> clazz) {
        for (CtMethod<?> method : clazz.getMethods()) {
            if ("bytesToHex".equals(method.getSimpleName())) {
                return true;
            }
        }
        return false;
    }
    
    private static void addBytesToHexMethod(CtClass<?> clazz, Factory factory) {
        try {
            // Create bytesToHex method
            CtMethod<String> method = factory.createMethod();
            method.setSimpleName("bytesToHex");
            method.addModifier(ModifierKind.PRIVATE);
            method.addModifier(ModifierKind.STATIC);
            
            // Add byte[] parameter
            CtTypeReference<byte[]> byteArrayRef = factory.Type().createReference(byte[].class);
            CtParameter<byte[]> param = factory.createParameter();
            param.setSimpleName("bytes");
            param.setType(byteArrayRef);
            method.addParameter(param);
            
            // Set return type
            method.setType(factory.Type().STRING);
            
            // Create method body
            String body = 
                "StringBuilder hex = new StringBuilder();\n" +
                "for (byte b : bytes) {\n" +
                "  String hexDigit = Integer.toHexString(0xff & b);\n" +
                "  if (hexDigit.length() == 1) {\n" +
                "    hex.append('0');\n" +
                "  }\n" +
                "  hex.append(hexDigit);\n" +
                "}\n" +
                "return hex.toString();";
            
            CtCodeSnippetStatement bodyStmt = factory.createCodeSnippetStatement(body);
            method.setBody(bodyStmt);
            
            // Add method to class
            clazz.addMethod(method);
            
        } catch (Exception e) {
            System.err.println("Failed to add bytesToHex method: " + e.getMessage());
        }
    }
}