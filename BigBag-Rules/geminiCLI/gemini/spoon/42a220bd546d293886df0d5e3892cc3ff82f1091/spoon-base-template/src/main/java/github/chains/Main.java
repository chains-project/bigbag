package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        File tempDir = new File("/workspace/minfraud-api-java/src/main/java/com/maxmind/minfraud/request");
        File digestUtilsFile = new File(tempDir, "DigestUtils.java");
        try (FileWriter writer = new FileWriter(digestUtilsFile)) {
            writer.write("package com.maxmind.minfraud.request;\n\n" +
                         "public class DigestUtils {\n" +
                         "    public static String md5Hex(String str) {\n" +
                         "        try {\n" +
                         "            java.security.MessageDigest md = java.security.MessageDigest.getInstance(\"MD5\");\n" +
                         "            byte[] array = md.digest(str.getBytes(java.nio.charset.StandardCharsets.UTF_8));\n" +
                         "            StringBuilder sb = new StringBuilder();\n" +
                         "            for (int i = 0; i < array.length; ++i) {\n" +
                         "                sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1,3));\n" +
                         "            }\n" +
                         "            return sb.toString();\n" +
                         "        } catch (java.security.NoSuchAlgorithmException ex) {\n" +
                         "            throw new RuntimeException(ex);\n" +
                         "        }\n" +
                         "    }\n" +
                         "}\n");
        }
        
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/minfraud-api-java/src/main/java/com/maxmind/minfraud/request/Account.java");
        launcher.addInputResource("/workspace/minfraud-api-java/src/main/java/com/maxmind/minfraud/request/Email.java");
        launcher.setSourceOutputDirectory("/workspace/minfraud-api-java/src/main/java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.buildModel();
        
        CtModel model = launcher.getModel();
        
        for (CtInvocation<?> inv : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (inv.getExecutable().getSimpleName().equals("md5Hex")) {
                spoon.reflect.reference.CtTypeReference<?> targetType = launcher.getFactory().Type().createReference("com.maxmind.minfraud.request.DigestUtils");
                inv.getExecutable().setDeclaringType(targetType.clone());
                if (inv.getTarget() instanceof spoon.reflect.code.CtTypeAccess) {
                    ((spoon.reflect.code.CtTypeAccess<?>) inv.getTarget()).getAccessedType().setPackage(launcher.getFactory().Package().getOrCreate("com.maxmind.minfraud.request").getReference());
                }
            }
        }
        
        for (CtCompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
            List<CtImport> importsToRemove = new ArrayList<>();
            for (CtImport imp : cu.getImports()) {
                if (imp.toString().contains("org.apache.commons.codec.digest.DigestUtils")) {
                    importsToRemove.add(imp);
                }
            }
            for (CtImport imp : importsToRemove) {
                cu.getImports().remove(imp);
            }
        }
        
        launcher.prettyprint();
        System.out.println("Transformation complete.");
    }
}
