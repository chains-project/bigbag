package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.ArrayType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws IOException {
        File file = new File("/workspace/singer/thrift-logger/src/main/java/com/pinterest/singer/client/logback/AppenderUtils.java");
        // Ensure file exists
        if (!file.exists()) {
             System.err.println("File not found: " + file.getAbsolutePath());
             System.exit(1);
        }

        CompilationUnit cu = StaticJavaParser.parse(file);

        // Add imports
        cu.addImport("org.apache.thrift.transport.TMemoryBuffer");
        cu.addImport("java.util.Arrays");

        // Find LogMessageEncoder class
        ClassOrInterfaceDeclaration appenderUtils = cu.getClassByName("AppenderUtils").orElseThrow(() -> new RuntimeException("AppenderUtils not found"));
        
        ClassOrInterfaceDeclaration encoderClass = appenderUtils.getMembers().stream()
                .filter(BodyDeclaration::isClassOrInterfaceDeclaration)
                .map(BodyDeclaration::asClassOrInterfaceDeclaration)
                .filter(c -> c.getNameAsString().equals("LogMessageEncoder"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("LogMessageEncoder not found"));

        // Remove old fields
        encoderClass.getFields().forEach(BodyDeclaration::remove);
        
        // Remove old methods
        encoderClass.getMethods().forEach(BodyDeclaration::remove);
        
        // Add headerBytes method
        MethodDeclaration headerBytes = encoderClass.addMethod("headerBytes", Modifier.Keyword.PUBLIC);
        headerBytes.addAnnotation("Override");
        headerBytes.setType(new ArrayType(PrimitiveType.byteType()));
        headerBytes.setBody(new BlockStmt().addStatement("return null;"));

        // Add footerBytes method
        MethodDeclaration footerBytes = encoderClass.addMethod("footerBytes", Modifier.Keyword.PUBLIC);
        footerBytes.addAnnotation("Override");
        footerBytes.setType(new ArrayType(PrimitiveType.byteType()));
        footerBytes.setBody(new BlockStmt().addStatement("return null;"));
        
        // Add encode method
        MethodDeclaration encode = encoderClass.addMethod("encode", Modifier.Keyword.PUBLIC);
        encode.addAnnotation("Override");
        encode.setType(new ArrayType(PrimitiveType.byteType()));
        encode.addParameter("LogMessage", "logMessage");
        
        BlockStmt encodeBody = new BlockStmt();
        encodeBody.addStatement("TMemoryBuffer buffer = new TMemoryBuffer(1024);");
        // Using TFastFramedTransport with TMemoryBuffer. 
        // bufferCapacity was 10 in original code.
        encodeBody.addStatement("TTransport transport = new TFastFramedTransport(buffer, 10);");
        encodeBody.addStatement("TProtocol protocol = new TBinaryProtocol(transport);");
        
        BlockStmt tryBlock = new BlockStmt();
        tryBlock.addStatement("logMessage.write(protocol);");
        tryBlock.addStatement("transport.flush();");
        tryBlock.addStatement("return Arrays.copyOf(buffer.getArray(), buffer.length());");
        
        BlockStmt catchBlock = new BlockStmt();
        catchBlock.addStatement("throw new RuntimeException(e);");
        
        com.github.javaparser.ast.stmt.TryStmt tryStmt = new com.github.javaparser.ast.stmt.TryStmt();
        tryStmt.setTryBlock(tryBlock);
        com.github.javaparser.ast.stmt.CatchClause catchClause = new com.github.javaparser.ast.stmt.CatchClause(
                new com.github.javaparser.ast.body.Parameter(new ClassOrInterfaceType(null, "TException"), "e"),
                catchBlock
        );
        tryStmt.setCatchClauses(new NodeList<>(catchClause));
        
        encodeBody.addStatement(tryStmt);
        encode.setBody(encodeBody);

        Files.write(file.toPath(), cu.toString().getBytes());
        System.out.println("Modified " + file.getAbsolutePath());
    }
}