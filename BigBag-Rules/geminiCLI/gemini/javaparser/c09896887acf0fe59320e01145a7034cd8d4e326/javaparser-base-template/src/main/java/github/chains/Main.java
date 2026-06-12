package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        Path path = Paths.get("/workspace/incrementals-tools/lib/src/main/java/io/jenkins/tools/incrementals/lib/UpdateChecker.java");
        CompilationUnit cu;
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            cu = StaticJavaParser.parse(in);
        }

        cu.findAll(FieldAccessExpr.class).forEach(fa -> {
            if (fa.getNameAsString().equals("status")) {
                // We want to replace .status with .getStatus()
                // Checking if it's the specific usage we are looking for might be safer but replacing all .status on objects to .getStatus() in this file might be okay given the error context.
                // However, let's look at the parent to be slightly more specific if needed, or just rely on the fact that 'status' field access failing implies we should use getter.
                // The error is specific to GHCompare.status.
                
                // Let's blindly replace field access "status" with method call "getStatus()" 
                // but only if it is likely the one.
                // The code is: ... .getCompare(branch, ghc.hash).status;
                
                // Let's check if scope is a method call to "getCompare" just to be safe, 
                // or we can just do it. Given the small file and specific error, likely safe.
                
                MethodCallExpr getStatusCall = new MethodCallExpr(fa.getScope(), "getStatus");
                fa.replace(getStatusCall);
            }
        });

        Files.write(path, cu.toString().getBytes());
        System.out.println("Transformation applied to " + path);
    }
}