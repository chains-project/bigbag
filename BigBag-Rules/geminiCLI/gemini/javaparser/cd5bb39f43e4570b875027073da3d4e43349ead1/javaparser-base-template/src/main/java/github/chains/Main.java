package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.File;
import java.nio.file.Files;

public class Main {
    public static void main(String[] args) throws Exception {
        // Modify MavenCompilerUtils.java
        File file1 = new File("/workspace/pgpverify-maven-plugin/src/main/java/org/simplify4u/plugins/utils/MavenCompilerUtils.java");
        CompilationUnit cu1 = StaticJavaParser.parse(file1);

        cu1.getImports().removeIf(i -> i.getNameAsString().equals("org.codehaus.plexus.util.xml.Xpp3Dom"));

        MethodDeclaration extractChildValue = cu1.getClassByName("MavenCompilerUtils").get()
                .getMethodsByName("extractChildValue").get(0);
        
        extractChildValue.getParameter(0).setType("Object");
        extractChildValue.setBody(StaticJavaParser.parseBlock(
            "{\n" +
            "    try {\n" +
            "        Object child = node.getClass().getMethod(\"getChild\", String.class).invoke(node, name);\n" +
            "        return child == null ? \"\" : (String) child.getClass().getMethod(\"getValue\").invoke(child);\n" +
            "    } catch (Exception e) {\n" +
            "        throw new RuntimeException(e);\n" +
            "    }\n" +
            "}"
        ));

        MethodDeclaration extractAnnotationProcessors = cu1.getClassByName("MavenCompilerUtils").get()
                .getMethodsByName("extractAnnotationProcessors").get(0);

        extractAnnotationProcessors.setBody(StaticJavaParser.parseBlock(
            "{\n" +
            "        requireNonNull(system);\n" +
            "        if (!checkCompilerPlugin(plugin)) {\n" +
            "            throw new IllegalArgumentException(\"Plugin is not '\" + GROUPID + \":\" + ARTIFACTID + \"'.\");\n" +
            "        }\n" +
            "        final Object config = plugin.getConfiguration();\n" +
            "        if (config == null) {\n" +
            "            return emptySet();\n" +
            "        }\n" +
            "        if (\"org.codehaus.plexus.util.xml.Xpp3Dom\".equals(config.getClass().getName()) || \"org.simplify4u.plugins.utils.MavenCompilerUtilsTest$MockXpp3Dom\".equals(config.getClass().getName())) {\n" +
            "            try {\n" +
            "                Object[] annotationProcessorPaths = (Object[]) config.getClass().getMethod(\"getChildren\", String.class).invoke(config, \"annotationProcessorPaths\");\n" +
            "                return stream(annotationProcessorPaths)\n" +
            "                        .flatMap(aggregate -> {\n" +
            "                            try {\n" +
            "                                return stream((Object[]) aggregate.getClass().getMethod(\"getChildren\", String.class).invoke(aggregate, \"path\"));\n" +
            "                            } catch (Exception e) {\n" +
            "                                throw new RuntimeException(e);\n" +
            "                            }\n" +
            "                        })\n" +
            "                        .map(processor -> system.createArtifact(\n" +
            "                                extractChildValue(processor, \"groupId\"),\n" +
            "                                extractChildValue(processor, \"artifactId\"),\n" +
            "                                extractChildValue(processor, \"version\"),\n" +
            "                                PACKAGING))\n" +
            "                        .filter(a -> !a.getGroupId().isEmpty())\n" +
            "                        .filter(a -> !a.getArtifactId().isEmpty())\n" +
            "                        .filter(a -> !a.getVersion().isEmpty())\n" +
            "                        .collect(Collectors.toSet());\n" +
            "            } catch (Exception e) {\n" +
            "                throw new RuntimeException(e);\n" +
            "            }\n" +
            "        }\n" +
            "        throw new UnsupportedOperationException(\"Please report that an unsupported type of configuration container\" +\n" +
            "                \" was encountered: \" + config.getClass());\n" +
            "}"
        ));

        Files.write(file1.toPath(), cu1.toString().getBytes());

        // Modify MavenCompilerUtilsTest.java
        File file2 = new File("/workspace/pgpverify-maven-plugin/src/test/java/org/simplify4u/plugins/utils/MavenCompilerUtilsTest.java");
        CompilationUnit cu2 = StaticJavaParser.parse(file2);

        cu2.getImports().removeIf(i -> i.getNameAsString().equals("org.codehaus.plexus.util.xml.Xpp3Dom"));
        cu2.addImport("java.util.List");
        cu2.addImport("java.util.ArrayList");

        cu2.getClassByName("MavenCompilerUtilsTest").get().addMember(StaticJavaParser.parseBodyDeclaration(
            "public static class MockXpp3Dom {\n" +
            "    private String name;\n" +
            "    private String value;\n" +
            "    private List<MockXpp3Dom> children = new ArrayList<>();\n" +
            "    public MockXpp3Dom(String name) { this.name = name; }\n" +
            "    public String getName() { return name; }\n" +
            "    public String getValue() { return value; }\n" +
            "    public void setValue(String value) { this.value = value; }\n" +
            "    public void addChild(MockXpp3Dom child) { children.add(child); }\n" +
            "    public MockXpp3Dom getChild(String name) {\n" +
            "        for (MockXpp3Dom child : children) {\n" +
            "            if (child.getName().equals(name)) return child;\n" +
            "        }\n" +
            "        return null;\n" +
            "    }\n" +
            "    public MockXpp3Dom[] getChildren(String name) {\n" +
            "        return children.stream().filter(c -> c.getName().equals(name)).toArray(MockXpp3Dom[]::new);\n" +
            "    }\n" +
            "}"
        ));

        MethodDeclaration createConfiguration = cu2.getClassByName("MavenCompilerUtilsTest").get()
                .getMethodsByName("createConfiguration").get(0);
        createConfiguration.setType("Object");
        createConfiguration.setBody(StaticJavaParser.parseBlock(
            "{\n" +
            "    final MockXpp3Dom config = new MockXpp3Dom(\"configuration\");\n" +
            "    final MockXpp3Dom annotationProcessorPaths = new MockXpp3Dom(\"annotationProcessorPaths\");\n" +
            "    annotationProcessorPaths.addChild(createPath(\"myGroupId\", \"myArtifactId\", \"1.2.3\"));\n" +
            "    annotationProcessorPaths.addChild(createPath(\"\", \"myArtifactId\", \"1.2.3\"));\n" +
            "    annotationProcessorPaths.addChild(createPath(\"myGroupId\", \"\", \"1.2.3\"));\n" +
            "    annotationProcessorPaths.addChild(createPath(null, \"myArtifactId\", \"1.2.3\"));\n" +
            "    annotationProcessorPaths.addChild(createPath(\"myGroupId\", null, \"1.2.3\"));\n" +
            "    annotationProcessorPaths.addChild(createPath(\"myGroupId\", \"myArtifactId\", null));\n" +
            "    config.addChild(annotationProcessorPaths);\n" +
            "    return config;\n" +
            "}"
        ));

        MethodDeclaration createPath = cu2.getClassByName("MavenCompilerUtilsTest").get()
                .getMethodsByName("createPath").get(0);
        createPath.setType("MockXpp3Dom");
        createPath.setBody(StaticJavaParser.parseBlock(
            "{\n" +
            "    final MockXpp3Dom path = new MockXpp3Dom(\"path\");\n" +
            "    if (groupId != null) {\n" +
            "        final MockXpp3Dom groupIdNode = new MockXpp3Dom(\"groupId\");\n" +
            "        groupIdNode.setValue(groupId);\n" +
            "        path.addChild(groupIdNode);\n" +
            "    }\n" +
            "    if (artifactId != null) {\n" +
            "        final MockXpp3Dom artifactIdNode = new MockXpp3Dom(\"artifactId\");\n" +
            "        artifactIdNode.setValue(artifactId);\n" +
            "        path.addChild(artifactIdNode);\n" +
            "    }\n" +
            "    if (version != null) {\n" +
            "        final MockXpp3Dom versionNode = new MockXpp3Dom(\"version\");\n" +
            "        versionNode.setValue(version);\n" +
            "        path.addChild(versionNode);\n" +
            "    }\n" +
            "    return path;\n" +
            "}"
        ));

        Files.write(file2.toPath(), cu2.toString().getBytes());
    }
}
