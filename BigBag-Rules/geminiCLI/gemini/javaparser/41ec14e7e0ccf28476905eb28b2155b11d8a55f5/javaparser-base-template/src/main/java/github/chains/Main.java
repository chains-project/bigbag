package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.File;
import java.io.PrintWriter;

public class Main {
    public static void main(String[] args) throws Exception {
        File file = new File("/workspace/wicket-crudifier/src/main/java/com/premiumminds/wicket/crudifier/form/elements/ListControlGroups.java");
        CompilationUnit cu = StaticJavaParser.parse(file);

        cu.findAll(MethodDeclaration.class).stream()
            .filter(m -> m.getNameAsString().equals("onInitialize"))
            .findFirst()
            .ifPresent(m -> {
                BlockStmt body = m.getBody().get();
                int startIndex = -1;
                int endIndex = -1;
                for (int i = 0; i < body.getStatements().size(); i++) {
                    Statement stmt = body.getStatements().get(i);
                    if (stmt.toString().contains("Validator validator = HibernateValidatorProperty.validatorFactory.getValidator();")) {
                        startIndex = i;
                    }
                    if (startIndex != -1 && stmt.toString().contains("for (String property : properties)")) {
                        endIndex = i;
                        break;
                    }
                }
                
                if (startIndex != -1 && endIndex != -1) {
                    for (int i = endIndex; i >= startIndex; i--) {
                        body.getStatements().remove(i);
                    }
                    
                    Statement replacement = StaticJavaParser.parseStatement(
                        "for(String property : properties){\n" +
                        "    java.beans.PropertyDescriptor descriptor = null;\n" +
                        "    try {\n" +
                        "        for (java.beans.PropertyDescriptor pd : java.beans.Introspector.getBeanInfo(modelClass).getPropertyDescriptors()) {\n" +
                        "            if (pd.getName().equals(property)) {\n" +
                        "                descriptor = pd;\n" +
                        "                break;\n" +
                        "            }\n" +
                        "        }\n" +
                        "    } catch (java.beans.IntrospectionException e) {\n" +
                        "        throw new RuntimeException(\"error getting property \" + property, e);\n" +
                        "    }\n" +
                        "    if (descriptor == null) {\n" +
                        "        throw new RuntimeException(\"error getting property \" + property);\n" +
                        "    }\n" +
                        "    boolean required = false;\n" +
                        "    try {\n" +
                        "        java.lang.reflect.Field field = modelClass.getDeclaredField(property);\n" +
                        "        java.lang.annotation.Annotation[] annotations = field.getAnnotations();\n" +
                        "        for (java.lang.annotation.Annotation a : annotations) {\n" +
                        "            String name = a.annotationType().getSimpleName();\n" +
                        "            if (\"NotNull\".equals(name) || \"NotEmpty\".equals(name) || \"NotBlank\".equals(name)) {\n" +
                        "                required = true;\n" +
                        "            }\n" +
                        "        }\n" +
                        "    } catch (NoSuchFieldException e) {\n" +
                        "    }\n" +
                        "    objectProperties.add(new ObjectProperties(descriptor, required));\n" +
                        "}"
                    );
                    body.getStatements().add(startIndex, replacement);
                }
            });

        try (PrintWriter out = new PrintWriter(file)) {
            out.println(cu.toString());
        }
    }
}
