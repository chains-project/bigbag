package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.StringReader;
import java.util.Optional;

public class TestRun {
    public static void main(String[] args) throws Exception {
        String code = "import com.gargoylesoftware.htmlunit.ScriptResult;\n" +
                      "\n" +
                      "public class TestScriptResult {\n" +
                      "    public String test1(Object result) {\n" +
                      "        ScriptResult scriptResult = new ScriptResult(result);\n" +
                      "        return scriptResult.getJavaScriptResult().toString();\n" +
                      "    }\n" +
                      "    \n" +
                      "    public Object test2(Object result) {\n" +
                      "        return new ScriptResult(result).getJavaScriptResult();\n" +
                      "    }\n" +
                      "    \n" +
                      "    public String test3(Object result) {\n" +
                      "        return new ScriptResult(result).getJavaScriptResult().toString();\n" +
                      "    }\n" +
                      "}";
        
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(new StringReader(code)).getResult().orElseThrow();
        
        System.out.println("Original code:");
        System.out.println(code);
        System.out.println("\n---\n");
        
        // Apply transformation
        HtmlUnitScriptResultTransformer transformer = new HtmlUnitScriptResultTransformer();
        transformer.visit(cu, null);
        
        System.out.println("Transformed code:");
        System.out.println(cu.toString());
    }
    
    private static class HtmlUnitScriptResultTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // First visit children to transform nested expressions
            super.visit(n, arg);
            
            // Check if this is .getJavaScriptResult()
            if (n.getNameAsString().equals("getJavaScriptResult")) {
                // Check if we're calling on a ScriptResult instance
                Optional<Expression> scopeOpt = n.getScope();
                if (scopeOpt.isPresent()) {
                    Expression scope = scopeOpt.get();
                    // Check if scope is new ScriptResult(...)
                    if (scope instanceof ObjectCreationExpr) {
                        ObjectCreationExpr creation = (ObjectCreationExpr) scope;
                        if (creation.getType().asString().equals("ScriptResult")) {
                            // Replace new ScriptResult(expr).getJavaScriptResult() with expr
                            NodeList<Expression> args = creation.getArguments();
                            if (args.size() == 1) {
                                modified = true;
                                return args.get(0);
                            }
                        }
                    } else {
                        // Variable or other expression of type ScriptResult
                        // Just remove .getJavaScriptResult() call
                        modified = true;
                        return scope;
                    }
                }
            }
            return n;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is new ScriptResult(...)
            if (n.getType().asString().equals("ScriptResult")) {
                // We'll handle this in visit(MethodCallExpr) if followed by .getJavaScriptResult()
                // Otherwise, we need to replace it here
                // First visit children
                super.visit(n, arg);
                
                // Check if parent is already a MethodCallExpr for .getJavaScriptResult()
                // If not, replace with argument
                NodeList<Expression> args = n.getArguments();
                if (args.size() == 1) {
                    modified = true;
                    return args.get(0);
                }
                return n;
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ClassOrInterfaceType n, Void arg) {
            // Replace ScriptResult type declarations with Object
            if (n.getNameAsString().equals("ScriptResult")) {
                modified = true;
                return new ClassOrInterfaceType("Object");
            }
            return super.visit(n, arg);
        }
    }
}
