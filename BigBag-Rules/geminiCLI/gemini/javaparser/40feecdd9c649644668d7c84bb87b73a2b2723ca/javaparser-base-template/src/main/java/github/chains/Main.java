package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.NodeList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

public class Main {
    public static void main(String[] args) throws IOException {
        Path path = Paths.get("/workspace/CoursesPortlet/courses-portlet-api/src/main/java/org/jasig/portlet/courses/model/xml/CourseMeetingWrapper.java");
        System.out.println("Processing file: " + path);
        CompilationUnit cu = StaticJavaParser.parse(path);

        new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(TryStmt n, Void arg) {
                NodeList<CatchClause> catches = n.getCatchClauses();
                Iterator<CatchClause> iter = catches.iterator();
                boolean removed = false;
                while (iter.hasNext()) {
                    CatchClause cc = iter.next();
                    if (cc.getParameter().getType().isClassOrInterfaceType()) {
                        ClassOrInterfaceType type = cc.getParameter().getType().asClassOrInterfaceType();
                        if (type.getNameAsString().equals("IOException")) {
                            iter.remove();
                            removed = true;
                            System.out.println("Removed IOException catch block.");
                        }
                    }
                }
                
                if (removed) {
                     if (n.getCatchClauses().isEmpty() && !n.getFinallyBlock().isPresent()) {
                         // Unwrap try block
                         System.out.println("Unwrapping try block.");
                         return n.getTryBlock(); 
                     }
                }
                
                return super.visit(n, arg);
            }
        }.visit(cu, null);

        Files.write(path, cu.toString().getBytes());
        System.out.println("File saved.");
    }
}