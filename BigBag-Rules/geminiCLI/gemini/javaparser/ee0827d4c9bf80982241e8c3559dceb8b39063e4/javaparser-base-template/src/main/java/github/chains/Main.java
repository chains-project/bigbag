package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.File;
import java.nio.file.Files;

public class Main {
    public static void main(String[] args) throws Exception {
        String baseDir = "/workspace/plexus-archiver/src/main/java/org/codehaus/plexus/archiver/";
        
        // 1. ByteArrayOutputStream.java
        File baosFile = new File(baseDir + "zip/ByteArrayOutputStream.java");
        CompilationUnit cu1 = StaticJavaParser.parse(baosFile);
        cu1.getImports().removeIf(i -> i.getNameAsString().equals("org.apache.commons.io.input.ClosedInputStream"));
        cu1.findAll(ObjectCreationExpr.class).forEach(oce -> {
            if (oce.getType().getNameAsString().equals("ClosedInputStream")) {
                oce.replace(StaticJavaParser.parseExpression("new java.io.InputStream() { @Override public int read() { return -1; } }"));
            }
        });
        Files.write(baosFile.toPath(), cu1.toString().getBytes());

        // 2. AbstractZipUnArchiver.java
        File azuFile = new File(baseDir + "zip/AbstractZipUnArchiver.java");
        CompilationUnit cu2 = StaticJavaParser.parse(azuFile);
        cu2.getImports().removeIf(i -> i.getNameAsString().equals("org.apache.commons.io.input.BoundedInputStream") || 
                                       i.getNameAsString().equals("org.apache.commons.io.input.CountingInputStream"));
        cu2.addImport("org.apache.commons.compress.utils.BoundedInputStream");
        cu2.addImport("org.apache.commons.compress.utils.CountingInputStream");
        cu2.findAll(MethodCallExpr.class).forEach(mc -> {
            if (mc.getNameAsString().equals("getByteCount")) {
                mc.setName("getBytesRead");
            }
        });
        Files.write(azuFile.toPath(), cu2.toString().getBytes());

        // 3. JarToolModularJarArchiver.java
        File jtmjaFile = new File(baseDir + "jar/JarToolModularJarArchiver.java");
        CompilationUnit cu3 = StaticJavaParser.parse(jtmjaFile);
        cu3.getImports().removeIf(i -> i.getNameAsString().equals("org.apache.commons.io.output.NullPrintStream"));
        cu3.findAll(FieldAccessExpr.class).forEach(fa -> {
            if (fa.getNameAsString().equals("NULL_PRINT_STREAM") && fa.getScope().toString().equals("NullPrintStream")) {
                fa.replace(StaticJavaParser.parseExpression("new java.io.PrintStream(new java.io.OutputStream() { @Override public void write(int b) {} })"));
            }
        });
        Files.write(jtmjaFile.toPath(), cu3.toString().getBytes());

        // 4. OffloadingOutputStream.java
        File oosFile = new File(baseDir + "zip/OffloadingOutputStream.java");
        CompilationUnit cu4 = StaticJavaParser.parse(oosFile);
        cu4.getImports().removeIf(i -> i.getNameAsString().equals("org.apache.commons.io.output.ThresholdingOutputStream"));
        ClassOrInterfaceDeclaration oosClass = cu4.getClassByName("OffloadingOutputStream").get();
        oosClass.getExtendedTypes().clear();
        oosClass.addExtendedType("OutputStream");
        
        // Remove super(threshold) and super.close()
        oosClass.getConstructors().forEach(cd -> {
            if (cd.getBody().getStatements().size() > 0) {
                Statement first = cd.getBody().getStatement(0);
                if (first instanceof ExplicitConstructorInvocationStmt) {
                    if (((ExplicitConstructorInvocationStmt) first).isThis()) {
                        // Do not remove this()
                    } else {
                        first.remove();
                    }
                }
            }
        });
        
        oosClass.getMethodsByName("close").forEach(md -> {
            md.getBody().ifPresent(body -> {
                body.getStatements().removeIf(stmt -> 
                    stmt instanceof ExpressionStmt && ((ExpressionStmt) stmt).getExpression().toString().equals("super.close()")
                );
            });
        });

        // Remove Overrides for getStream and thresholdReached
        oosClass.getMethodsByName("getStream").forEach(md -> md.getAnnotations().removeIf(a -> a.getNameAsString().equals("Override")));
        oosClass.getMethodsByName("thresholdReached").forEach(md -> md.getAnnotations().removeIf(a -> a.getNameAsString().equals("Override")));

        // Add fields
        oosClass.addField("int", "threshold", com.github.javaparser.ast.Modifier.Keyword.PRIVATE);
        oosClass.addField("long", "written", com.github.javaparser.ast.Modifier.Keyword.PRIVATE);
        oosClass.addField("boolean", "thresholdExceeded", com.github.javaparser.ast.Modifier.Keyword.PRIVATE);

        // Add ThresholdingOutputStream methods
        oosClass.addMember(StaticJavaParser.parseBodyDeclaration(
            "public void write(int b) throws IOException {\n" +
            "    checkThreshold(1);\n" +
            "    getStream().write(b);\n" +
            "    written++;\n" +
            "}"
        ));
        oosClass.addMember(StaticJavaParser.parseBodyDeclaration(
            "public void write(byte[] b) throws IOException {\n" +
            "    checkThreshold(b.length);\n" +
            "    getStream().write(b);\n" +
            "    written += b.length;\n" +
            "}"
        ));
        oosClass.addMember(StaticJavaParser.parseBodyDeclaration(
            "public void write(byte[] b, int off, int len) throws IOException {\n" +
            "    checkThreshold(len);\n" +
            "    getStream().write(b, off, len);\n" +
            "    written += len;\n" +
            "}"
        ));
        oosClass.addMember(StaticJavaParser.parseBodyDeclaration(
            "public void flush() throws IOException {\n" +
            "    getStream().flush();\n" +
            "}"
        ));
        oosClass.addMember(StaticJavaParser.parseBodyDeclaration(
            "protected void checkThreshold(int count) throws IOException {\n" +
            "    if (!thresholdExceeded && written + count > threshold) {\n" +
            "        thresholdExceeded = true;\n" +
            "        thresholdReached();\n" +
            "    }\n" +
            "}"
        ));
        
        // Also need to initialize this.threshold in constructor
        oosClass.getConstructors().forEach(cd -> {
            if (cd.getParameters().size() == 5) {
                cd.getBody().addStatement(0, StaticJavaParser.parseStatement("this.threshold = threshold;"));
            }
        });
        
        Files.write(oosFile.toPath(), cu4.toString().getBytes());

        System.out.println("Transformation complete.");
    }
}
