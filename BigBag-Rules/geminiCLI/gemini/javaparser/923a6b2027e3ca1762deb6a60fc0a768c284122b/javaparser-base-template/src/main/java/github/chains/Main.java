package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.ImportDeclaration;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        fixMkQueryMatchers();
        fixRestResponse();
    }

    private static void fixMkQueryMatchers() throws Exception {
        File file = new File("/workspace/jcabi-http/src/main/java/com/jcabi/http/mock/MkQueryMatchers.java");
        CompilationUnit cu = StaticJavaParser.parse(file);
        
        for (ImportDeclaration id : cu.findAll(ImportDeclaration.class)) {
            if (id.getNameAsString().equals("org.hamcrest.Matchers")) {
                id.remove();
            }
        }
        
        cu.findAll(MethodCallExpr.class).forEach(mc -> {
            if (mc.getNameAsString().equals("hasProperty") && mc.getScope().isPresent()) {
                String scope = mc.getScope().get().toString();
                if (scope.equals("Matchers") || scope.equals("Matchers.<URI>")) {
                    String prop = mc.getArgument(0).asStringLiteralExpr().getValue();
                    String argName = mc.getArgument(1).toString();
                    
                    String replacement = "";
                    if (prop.equals("rawPath")) {
                        replacement = "new org.hamcrest.FeatureMatcher<java.net.URI, String>(" + argName + ", \"rawPath\", \"rawPath\") {\n" +
                                      "    @Override\n" +
                                      "    protected String featureValueOf(java.net.URI actual) {\n" +
                                      "        return actual.getRawPath();\n" +
                                      "    }\n" +
                                      "}";
                    } else if (prop.equals("rawQuery")) {
                        replacement = "new org.hamcrest.FeatureMatcher<java.net.URI, String>(" + argName + ", \"rawQuery\", \"rawQuery\") {\n" +
                                      "    @Override\n" +
                                      "    protected String featureValueOf(java.net.URI actual) {\n" +
                                      "        return actual.getRawQuery();\n" +
                                      "    }\n" +
                                      "}";
                    }
                    if (!replacement.isEmpty()) {
                        Expression replacementExpr = StaticJavaParser.parseExpression(replacement);
                        mc.replace(replacementExpr);
                    }
                }
            }
        });
        
        Files.write(file.toPath(), cu.toString().getBytes("UTF-8"));
    }

    private static void fixRestResponse() throws Exception {
        File file = new File("/workspace/jcabi-http/src/main/java/com/jcabi/http/response/RestResponse.java");
        CompilationUnit cu = StaticJavaParser.parse(file);
        
        for (ImportDeclaration id : cu.findAll(ImportDeclaration.class)) {
            if (id.getNameAsString().equals("org.hamcrest.Matchers")) {
                id.setName("org.hamcrest.CoreMatchers");
            }
        }
        
        cu.findAll(MethodCallExpr.class).forEach(mc -> {
            if (mc.getScope().isPresent() && mc.getScope().get().toString().equals("Matchers")) {
                if (mc.getScope().get() instanceof NameExpr) {
                    ((NameExpr) mc.getScope().get()).setName("CoreMatchers");
                }
            }
        });
        
        // Now replace CoreMatchers.not(CoreMatchers.emptyIterableOf(String.class))
        cu.findAll(MethodCallExpr.class).forEach(mc -> {
            if (mc.getNameAsString().equals("not") && mc.getScope().isPresent() && mc.getScope().get().toString().equals("CoreMatchers")) {
                if (mc.getArguments().size() == 1 && mc.getArgument(0) instanceof MethodCallExpr) {
                    MethodCallExpr inner = (MethodCallExpr) mc.getArgument(0);
                    if (inner.getNameAsString().equals("emptyIterableOf")) {
                        String replacement = "new org.hamcrest.TypeSafeMatcher<Iterable<String>>() {\n" +
                                             "    @Override\n" +
                                             "    protected boolean matchesSafely(Iterable<String> item) {\n" +
                                             "        return item.iterator().hasNext();\n" +
                                             "    }\n" +
                                             "    @Override\n" +
                                             "    public void describeTo(org.hamcrest.Description description) {\n" +
                                             "        description.appendText(\"not empty iterable\");\n" +
                                             "    }\n" +
                                             "}";
                        Expression replacementExpr = StaticJavaParser.parseExpression(replacement);
                        mc.replace(replacementExpr);
                    }
                }
            }
        });
        
        Files.write(file.toPath(), cu.toString().getBytes("UTF-8"));
    }
}
