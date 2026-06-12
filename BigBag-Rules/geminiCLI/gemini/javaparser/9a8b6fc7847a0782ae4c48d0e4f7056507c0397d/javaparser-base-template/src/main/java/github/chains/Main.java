package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        fixGeoStoreDigestPasswordEncoder();
        fixGeoStorePBEPasswordEncoder();
    }

    private static void fixGeoStoreDigestPasswordEncoder() throws IOException {
        Path path = Paths.get("/workspace/geostore/src/core/security/src/main/java/it/geosolutions/geostore/core/security/password/GeoStoreDigestPasswordEncoder.java");
        CompilationUnit cu = StaticJavaParser.parse(path);

        // Remove invalid import
        cu.getImports().removeIf(i -> i.getNameAsString().equals("org.jasypt.spring.security.PasswordEncoder"));
        // Add correct import if missing
        if (cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals("org.acegisecurity.providers.encoding.PasswordEncoder"))) {
            cu.addImport("org.acegisecurity.providers.encoding.PasswordEncoder");
        }
        if (cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals("org.springframework.dao.DataAccessException"))) {
            cu.addImport("org.springframework.dao.DataAccessException");
        }

        // Fix createStringEncoder method
        cu.findAll(MethodDeclaration.class, m -> m.getNameAsString().equals("createStringEncoder")).forEach(m -> {
            String code = "{\n" +
                    "    final StrongPasswordEncryptor encryptor = new StrongPasswordEncryptor();\n" +
                    "    return new PasswordEncoder() {\n" +
                    "        public String encodePassword(String rawPass, Object salt) throws DataAccessException {\n" +
                    "            return encryptor.encryptPassword(rawPass);\n" +
                    "        }\n" +
                    "        public boolean isPasswordValid(String encPass, String rawPass, Object salt) throws DataAccessException {\n" +
                    "            return encryptor.checkPassword(rawPass, encPass);\n" +
                    "        }\n" +
                    "    };\n" +
                    "}";
            
            BlockStmt newBody = StaticJavaParser.parseBlock(code);
            m.setBody(newBody);
        });

        Files.write(path, cu.toString().getBytes());
        System.out.println("Fixed GeoStoreDigestPasswordEncoder.java");
    }

    private static void fixGeoStorePBEPasswordEncoder() throws IOException {
        Path path = Paths.get("/workspace/geostore/src/core/security/src/main/java/it/geosolutions/geostore/core/security/password/GeoStorePBEPasswordEncoder.java");
        CompilationUnit cu = StaticJavaParser.parse(path);

        // Remove invalid import
        cu.getImports().removeIf(i -> i.getNameAsString().equals("org.jasypt.spring.security.PBEPasswordEncoder"));
        
        // Ensure imports
        if (cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals("org.acegisecurity.providers.encoding.PasswordEncoder"))) {
            cu.addImport("org.acegisecurity.providers.encoding.PasswordEncoder");
        }
        if (cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals("org.springframework.dao.DataAccessException"))) {
            cu.addImport("org.springframework.dao.DataAccessException");
        }


        // Fix createStringEncoder method
        cu.findAll(MethodDeclaration.class, m -> m.getNameAsString().equals("createStringEncoder")).forEach(m -> {
             String code = "{\n" +
                     "    byte[] password = lookupPasswordFromKeyStore();\n" +
                     "    char[] chars = toChars(password);\n" +
                     "    try {\n" +
                     "        stringEncrypter = new StandardPBEStringEncryptor();\n" +
                     "        stringEncrypter.setPasswordCharArray(chars);\n" +
                     "        if (getProviderName() != null && !getProviderName().isEmpty()) {\n" +
                     "            stringEncrypter.setProviderName(getProviderName());\n" +
                     "        }\n" +
                     "        stringEncrypter.setAlgorithm(getAlgorithm());\n" +
                     "        return new PasswordEncoder() {\n" +
                     "            public String encodePassword(String rawPass, Object salt) throws DataAccessException {\n" +
                     "                return stringEncrypter.encrypt(rawPass);\n" +
                     "            }\n" +
                     "            public boolean isPasswordValid(String encPass, String rawPass, Object salt) throws DataAccessException {\n" +
                     "                return stringEncrypter.decrypt(encPass).equals(rawPass);\n" +
                     "            }\n" +
                     "        };\n" +
                     "    } finally {\n" +
                     "        scramble(password);\n" +
                     "        scramble(chars);\n" +
                     "    }\n" +
                     "}";

            BlockStmt newBody = StaticJavaParser.parseBlock(code);
            m.setBody(newBody);
        });

        Files.write(path, cu.toString().getBytes());
        System.out.println("Fixed GeoStorePBEPasswordEncoder.java");
    }
}