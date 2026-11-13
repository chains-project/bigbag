package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.PrettyPrinter;

import java.io.StringReader;
import java.util.Optional;

/**
 * This class demonstrates various JavaParser transformation techniques.
 * It shows how to create visitors that modify AST nodes.
 */
public class TransformationExamples {
    
    public static void main(String[] args) {
        System.out.println("=== JavaParser Transformation Examples ===\n");
        
        // Example 1: Simple method renaming
        example1();
        
        // Example 2: Adding annotations
        example2();
        
        // Example 3: Modifying method bodies
        example3();
        
        // Example 4: Adding logging to methods
        example4();
        
        // Example 5: Refactoring variable names
        example5();
    }
    
    private static void example1() {
        System.out.println("Example 1: Simple Method Renaming");
        System.out.println("===================================");
        
        String code = "public class Example {\n" +
                     "    public void oldMethod() {\n" +
                     "        System.out.println(\"Hello\");\n" +
                     "    }\n" +
                     "}";
        
        try {
            CompilationUnit cu = new JavaParser().parse(new StringReader(code)).getResult().orElseThrow();
            
            class MethodRenamer extends VoidVisitorAdapter<Void> {
                @Override
                public void visit(MethodDeclaration md, Void arg) {
                    super.visit(md, arg);
                    if (md.getNameAsString().equals("oldMethod")) {
                        md.setName("newMethod");
                    }
                }
            }
            
            cu.accept(new MethodRenamer(), null);
            System.out.println(new PrettyPrinter().print(cu));
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println();
    }
    
    private static void example2() {
        System.out.println("Example 2: Adding Annotations");
        System.out.println("==============================");
        
        String code = "public class Service {\n" +
                     "    public void process() {\n" +
                     "        // do something\n" +
                     "    }\n" +
                     "}";
        
        try {
            CompilationUnit cu = new JavaParser().parse(new StringReader(code)).getResult().orElseThrow();
            
            class AnnotationAdder extends VoidVisitorAdapter<Void> {
                @Override
                public void visit(MethodDeclaration md, Void arg) {
                    super.visit(md, arg);
                    // Add @Override annotation if not already present
                    boolean hasOverride = md.getAnnotations().stream()
                        .anyMatch(a -> a.getNameAsString().equals("Override"));
                    if (!hasOverride) {
                        md.addAnnotation("Override");
                    }
                }
            }
            
            cu.accept(new AnnotationAdder(), null);
            System.out.println(new PrettyPrinter().print(cu));
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println();
    }
    
    private static void example3() {
        System.out.println("Example 3: Modifying Method Bodies");
        System.out.println("===================================");
        
        String code = "public class MathUtils {\n" +
                     "    public int square(int x) {\n" +
                     "        return x * x;\n" +
                     "    }\n" +
                     "}";
        
        try {
            CompilationUnit cu = new JavaParser().parse(new StringReader(code)).getResult().orElseThrow();
            
            class BodyModifier extends VoidVisitorAdapter<Void> {
                @Override
                public void visit(MethodDeclaration md, Void arg) {
                    super.visit(md, arg);
                    if (md.getNameAsString().equals("square")) {
                        // Change the return statement
                        ReturnStmt oldReturn = (ReturnStmt) md.getBody().get().getStatements().get(0);
                        
                        // Create a new return statement: return x * x * x;
                        BinaryExpr multiplication = new BinaryExpr(
                            new BinaryExpr(
                                new NameExpr("x"),
                                new NameExpr("x"),
                                BinaryExpr.Operator.MULTIPLY
                            ),
                            new NameExpr("x"),
                            BinaryExpr.Operator.MULTIPLY
                        );
                        
                        ReturnStmt newReturn = new ReturnStmt(multiplication);
                        md.getBody().get().setStatement(0, newReturn);
                        md.setName("cube"); // Also rename the method
                    }
                }
            }
            
            cu.accept(new BodyModifier(), null);
            System.out.println(new PrettyPrinter().print(cu));
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println();
    }
    
    private static void example4() {
        System.out.println("Example 4: Adding Logging to Methods");
        System.out.println("=====================================");
        
        String code = "public class DataProcessor {\n" +
                     "    public void processData(String input) {\n" +
                     "        // process data\n" +
                     "        String result = input.toUpperCase();\n" +
                     "    }\n" +
                     "}";
        
        try {
            CompilationUnit cu = new JavaParser().parse(new StringReader(code)).getResult().orElseThrow();
            
            class LoggingAdder extends VoidVisitorAdapter<Void> {
                @Override
                public void visit(MethodDeclaration md, Void arg) {
                    super.visit(md, arg);
                    
                    if (md.getBody().isPresent()) {
                        BlockStmt body = md.getBody().get();
                        
                        // Create logging statement at the beginning
                        MethodCallExpr logCall = new MethodCallExpr(
                            "System.out.println",
                            new StringLiteralExpr("Method " + md.getNameAsString() + " called")
                        );
                        
                        ExpressionStmt logStmt = new ExpressionStmt(logCall);
                        body.getStatements().add(0, logStmt);
                        
                        // Create logging statement at the end
                        MethodCallExpr logEndCall = new MethodCallExpr(
                            "System.out.println",
                            new StringLiteralExpr("Method " + md.getNameAsString() + " finished")
                        );
                        
                        ExpressionStmt logEndStmt = new ExpressionStmt(logEndCall);
                        body.getStatements().add(logEndStmt);
                    }
                }
            }
            
            cu.accept(new LoggingAdder(), null);
            System.out.println(new PrettyPrinter().print(cu));
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println();
    }
    
    private static void example5() {
        System.out.println("Example 5: Refactoring Variable Names");
        System.out.println("=====================================");
        
        String code = "public class Example {\n" +
                     "    public void calculate() {\n" +
                     "        int a = 10;\n" +
                     "        int b = 20;\n" +
                     "        int result = a + b;\n" +
                     "    }\n" +
                     "}";
        
        try {
            CompilationUnit cu = new JavaParser().parse(new StringReader(code)).getResult().orElseThrow();
            
            class VariableRenamer extends VoidVisitorAdapter<Void> {
                @Override
                public void visit(VariableDeclarationExpr vde, Void arg) {
                    super.visit(vde, arg);
                    for (VariableDeclarator vd : vde.getVariables()) {
                        String oldName = vd.getNameAsString();
                        if (oldName.equals("a")) {
                            vd.setName("firstNumber");
                        } else if (oldName.equals("b")) {
                            vd.setName("secondNumber");
                        } else if (oldName.equals("result")) {
                            vd.setName("sum");
                        }
                    }
                }
            }
            
            cu.accept(new VariableRenamer(), null);
            System.out.println(new PrettyPrinter().print(cu));
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println();
    }
}