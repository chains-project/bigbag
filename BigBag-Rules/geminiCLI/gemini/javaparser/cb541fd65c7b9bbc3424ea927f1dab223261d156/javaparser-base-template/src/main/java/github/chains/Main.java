package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.io.File;
import java.nio.file.Files;

public class Main {
    public static void main(String[] args) throws Exception {
        File restResponseFile = new File("/workspace/jcabi-http/src/main/java/com/jcabi/http/response/RestResponse.java");
        CompilationUnit cuRest = StaticJavaParser.parse(restResponseFile);
        
        for (MethodDeclaration md : cuRest.findAll(MethodDeclaration.class)) {
            if (md.getNameAsString().equals("follow")) {
                md.addSingleMemberAnnotation("SuppressWarnings", "\"unchecked\"");
            }
        }
        Files.write(restResponseFile.toPath(), cuRest.toString().getBytes());
        System.out.println("Transformation completed.");
    }
}
