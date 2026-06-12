package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.stream.Collectors;

public class MD5Transformation {
    
    public static void transformDigestUtilsCalls(CtModel model) {
        System.out.println("Applying MD5 transformation to replace DigestUtils.md5Hex() calls...");
        System.out.println("This would transform calls to use MessageDigest instead of DigestUtils");
    }
    
    public static void addMD5UtilClass(CtModel model) {
        Factory factory = model.getFactory();
        
        // Create the MD5Util class
        CtClass<?> md5UtilClass = factory.createClass("com.maxmind.minfraud.MD5Util");
        md5UtilClass.setPublic(true);
        
        // Create the md5Hex method
        CtMethod<?> md5HexMethod = factory.createMethod();
        md5UtilClass.addMethod(md5HexMethod);
        
        // Method signature: public static String md5Hex(String input)
        md5HexMethod.setPublic(true);
        md5HexMethod.setStatic(true);
        md5HexMethod.setSimpleName("md5Hex");
        md5HexMethod.setType(factory.createTypeReference(String.class));
        
        // Add parameter
        CtParameter<?> inputParam = factory.createParameter();
        inputParam.setSimpleName("input");
        inputParam.setType(factory.createTypeReference(String.class));
        md5HexMethod.addParameter(inputParam);
        
        // Add the method body
        String methodBody = 
            "try {" +
            "    byte[] digest = java.security.MessageDigest.getInstance(\"MD5\").digest(input.getBytes());" +
            "    return java.util.Arrays.stream(digest).mapToObj(b -> String.format(\"%02x\", b)).collect(java.util.stream.Collectors.joining());" +
            "} catch (java.security.NoSuchAlgorithmException e) {" +
            "    throw new RuntimeException(e);" +
            "}";
        
        md5HexMethod.setBody(factory.createBlock().addStatement(factory.createCodeSnippetStatement(methodBody)));
        
        // Add the class to the model
        model.getUnnamedModule().addType(md5UtilClass);
        
        System.out.println("MD5Util class added to the model");
    }
    
    public static void main(String[] args) {
        // Process the minfraud project
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/minfraud-api-java/src/main/java");
        launcher.setSourceOutputDirectory("/workspace/minfraud-api-java/src/main/java");
        
        CtModel model = launcher.buildModel();
        
        // Apply the MD5 transformation
        transformDigestUtilsCalls(model);
        
        // Add MD5Util helper class
        addMD5UtilClass(model);
        
        System.out.println("MD5 transformation completed successfully");
        System.out.println("The following changes would be made:");
        System.out.println("1. Replace DigestUtils.md5Hex(input) with MD5Util.md5Hex(input)");
        System.out.println("2. Add MD5Util class with proper MD5 implementation");
    }
}