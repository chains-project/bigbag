package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.io.File;
import java.nio.file.Files;

public class Main {
    public static void main(String[] args) throws Exception {
        processFile("/workspace/minfraud-api-java/src/main/java/com/maxmind/minfraud/request/Account.java", "Account");
        processFile("/workspace/minfraud-api-java/src/main/java/com/maxmind/minfraud/request/Email.java", "Email");
    }

    private static void processFile(String path, String className) throws Exception {
        File file = new File(path);
        CompilationUnit cu = StaticJavaParser.parse(file);
        
        cu.getImports().removeIf(i -> i.getNameAsString().equals("org.apache.commons.codec.digest.DigestUtils"));
        
        cu.findAll(MethodCallExpr.class).forEach(mc -> {
            if (mc.getScope().isPresent() && mc.getScope().get().toString().equals("DigestUtils") && mc.getNameAsString().equals("md5Hex")) {
                mc.setScope(null);
            }
        });
        
        MethodDeclaration md5Method = StaticJavaParser.parseMethodDeclaration(
            "private static String md5Hex(String s) {\n" +
            "    try {\n" +
            "        java.security.MessageDigest md = java.security.MessageDigest.getInstance(\"MD5\");\n" +
            "        md.update(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));\n" +
            "        return String.format(\"%032x\", new java.math.BigInteger(1, md.digest()));\n" +
            "    } catch (Exception e) {\n" +
            "        throw new RuntimeException(e);\n" +
            "    }\n" +
            "}"
        );
        
        cu.getClassByName(className).ifPresent(c -> c.addMember(md5Method));
        Files.write(file.toPath(), cu.toString().getBytes());
    }
}
