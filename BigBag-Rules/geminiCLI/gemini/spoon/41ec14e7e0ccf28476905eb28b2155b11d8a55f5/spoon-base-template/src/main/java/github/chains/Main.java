package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtForEach;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting Spoon transformation...");
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/wicket-crudifier/src/main/java/com/premiumminds/wicket/crudifier/form/elements/ListControlGroups.java");
        launcher.addInputResource("/workspace/wicket-crudifier/src/main/java/com/premiumminds/wicket/crudifier/view/CrudifierView.java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        launcher.buildModel();
        CtModel model = launcher.getModel();
        Factory factory = launcher.getFactory();

        // 1. Process CrudifierView
        CtClass<?> viewClass = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class)).stream()
                .filter(c -> c.getSimpleName().equals("CrudifierView"))
                .findFirst().orElse(null);
        if (viewClass != null) {
            CtMethod<?> getPropsMethod = viewClass.getMethodsByName("getPropertiesByOrder").get(0);
            String newBody = "{\n" +
                    "    java.util.Set<String> properties = new java.util.LinkedHashSet<String>();\n" +
                    "    for (String property : entitySettings.getOrderOfFields()) {\n" +
                    "        if (!entitySettings.getHiddenFields().contains(property)) {\n" +
                    "            properties.add(property);\n" +
                    "        }\n" +
                    "    }\n" +
                    "    try {\n" +
                    "        for (java.beans.PropertyDescriptor descriptor : java.beans.Introspector.getBeanInfo(modelClass).getPropertyDescriptors()) {\n" +
                    "            if (!entitySettings.getHiddenFields().contains(descriptor.getName()) &&\n" +
                    "                !properties.contains(descriptor.getName()) &&\n" +
                    "                !descriptor.getName().equals(\"class\")) {\n" +
                    "                properties.add(descriptor.getName());\n" +
                    "            }\n" +
                    "        }\n" +
                    "    } catch (java.lang.Exception e) {\n" +
                    "        throw new java.lang.RuntimeException(e);\n" +
                    "    }\n" +
                    "    return properties;\n" +
                    "}";
            getPropsMethod.setBody(factory.Code().createCodeSnippetStatement(newBody));
        }

        // 2. Process ListControlGroups
        CtClass<?> listClass = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class)).stream()
                .filter(c -> c.getSimpleName().equals("ListControlGroups"))
                .findFirst().orElse(null);
        if (listClass != null) {
            // Replace getPropertiesByOrder
            CtMethod<?> getPropsMethod = listClass.getMethodsByName("getPropertiesByOrder").get(0);
            String newBodyProps = "{\n" +
                    "    java.util.Set<String> properties = new java.util.LinkedHashSet<String>();\n" +
                    "    for (String property : entitySettings.getOrderOfFields()) {\n" +
                    "        if (!entitySettings.getHiddenFields().contains(property)) {\n" +
                    "            properties.add(property);\n" +
                    "        }\n" +
                    "    }\n" +
                    "    try {\n" +
                    "        for (java.beans.PropertyDescriptor descriptor : java.beans.Introspector.getBeanInfo(modelClass).getPropertyDescriptors()) {\n" +
                    "            if (!entitySettings.getHiddenFields().contains(descriptor.getName()) &&\n" +
                    "                !properties.contains(descriptor.getName()) &&\n" +
                    "                !descriptor.getName().equals(\"class\")) {\n" +
                    "                properties.add(descriptor.getName());\n" +
                    "            }\n" +
                    "        }\n" +
                    "    } catch (java.lang.Exception e) {\n" +
                    "        throw new java.lang.RuntimeException(e);\n" +
                    "    }\n" +
                    "    return properties;\n" +
                    "}";
            getPropsMethod.setBody(factory.Code().createCodeSnippetStatement(newBodyProps));

            // Modify onInitialize
            CtMethod<?> onInitMethod = listClass.getMethodsByName("onInitialize").get(0);
            CtBlock<?> body = onInitMethod.getBody();

            // Find and delete the Validator and BeanDescriptor local variables
            List<CtLocalVariable> vars = body.getElements(new TypeFilter<>(CtLocalVariable.class));
            for (CtLocalVariable var : vars) {
                if (var.getSimpleName().equals("validator") || var.getSimpleName().equals("constraintDescriptors")) {
                    var.delete();
                }
            }

            // Find the for loop over properties
            CtForEach forEachProperties = body.getElements(new TypeFilter<CtForEach>(CtForEach.class)).stream()
                    .filter(loop -> loop.getVariable().getSimpleName().equals("property"))
                    .findFirst().orElse(null);
            
            if (forEachProperties != null) {
                String newLoopBody = "{\n" +
                        "    java.beans.PropertyDescriptor descriptor = null;\n" +
                        "    try {\n" +
                        "        for (java.beans.PropertyDescriptor pd : java.beans.Introspector.getBeanInfo(getModel().getObject().getClass()).getPropertyDescriptors()) {\n" +
                        "            if (pd.getName().equals(property)) {\n" +
                        "                descriptor = pd;\n" +
                        "                break;\n" +
                        "            }\n" +
                        "        }\n" +
                        "    } catch (java.lang.Exception e) {\n" +
                        "        throw new java.lang.RuntimeException(\"error getting property \" + property, e);\n" +
                        "    }\n" +
                        "    boolean required = false;\n" +
                        "    try {\n" +
                        "        java.lang.reflect.Field field = modelClass.getDeclaredField(descriptor.getName());\n" +
                        "        for (java.lang.annotation.Annotation ann : field.getAnnotations()) {\n" +
                        "            String name = ann.annotationType().getSimpleName();\n" +
                        "            if (\"NotNull\".equals(name) || \"NotEmpty\".equals(name) || \"NotBlank\".equals(name)) {\n" +
                        "                required = true;\n" +
                        "                break;\n" +
                        "            }\n" +
                        "        }\n" +
                        "    } catch (java.lang.Exception e) {\n" +
                        "    }\n" +
                        "    if (!required && descriptor != null && descriptor.getReadMethod() != null) {\n" +
                        "        for (java.lang.annotation.Annotation ann : descriptor.getReadMethod().getAnnotations()) {\n" +
                        "            String name = ann.annotationType().getSimpleName();\n" +
                        "            if (\"NotNull\".equals(name) || \"NotEmpty\".equals(name) || \"NotBlank\".equals(name)) {\n" +
                        "                required = true;\n" +
                        "                break;\n" +
                        "            }\n" +
                        "        }\n" +
                        "    }\n" +
                        "    objectProperties.add(new ObjectProperties(descriptor, required));\n" +
                        "}";
                forEachProperties.setBody(factory.Code().createCodeSnippetStatement(newLoopBody));
            }
        }

        launcher.setSourceOutputDirectory("/workspace/wicket-crudifier/spooned");
        launcher.prettyprint();
        System.out.println("Spoon transformation complete. Running post-processing to clean up formatting and unused imports...");
        
        try {
            java.nio.file.Path p1 = java.nio.file.Paths.get("/workspace/wicket-crudifier/spooned/com/premiumminds/wicket/crudifier/form/elements/ListControlGroups.java");
            String c1 = new String(java.nio.file.Files.readAllBytes(p1), "UTF-8");
            c1 = c1.replace("            return properties;\n        };\n    }", "            return properties;\n        }\n    }");
            c1 = c1 = c1.replaceAll("import javax\\\\.validation.*", "");
            c1 = c1 = c1.replaceAll("import javax\\\\.validation.*", "");
            java.nio.file.Files.write(p1, c1.getBytes("UTF-8"));

            java.nio.file.Path p2 = java.nio.file.Paths.get("/workspace/wicket-crudifier/spooned/com/premiumminds/wicket/crudifier/view/CrudifierView.java");
            String c2 = new String(java.nio.file.Files.readAllBytes(p2), "UTF-8");
            c2 = c2.replace("            return properties;\n        };\n    }", "            return properties;\n        }\n    }");
            c2 = c2 = c2.replaceAll("import javax\\\\.validation.*", "");
            c2 = c2 = c2.replaceAll("import javax\\\\.validation.*", "");
            java.nio.file.Files.write(p2, c2.getBytes("UTF-8"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}