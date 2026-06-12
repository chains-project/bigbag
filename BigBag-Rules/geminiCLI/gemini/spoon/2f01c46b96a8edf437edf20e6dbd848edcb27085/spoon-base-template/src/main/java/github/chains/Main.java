package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCodeSnippetStatement;
import java.util.stream.Collectors;
import java.util.List;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.declaration.CtImport;
import spoon.compiler.Environment;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class Main {
    public static void main(String[] args) throws Exception {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/depclean/depclean-maven-plugin/src/main/java/se/kth/depclean/wrapper/MavenDependencyManager.java");
        
        Environment env = launcher.getEnvironment();
        env.setNoClasspath(true);
        env.setAutoImports(true);
        env.setPrettyPrinterCreator(() -> new SniperJavaPrettyPrinter(env));

        launcher.setSourceOutputDirectory("/workspace/depclean/depclean-maven-plugin/src/main/java");
        launcher.buildModel();

        CtClass<?> clazz = launcher.getFactory().Class().get("se.kth.depclean.wrapper.MavenDependencyManager");
        CtMethod<?> method1 = clazz.getMethodsByName("collectUsedClassesFromProcessors").get(0);
        
        CtCodeSnippetStatement snippet1 = launcher.getFactory().Code().createCodeSnippetStatement(
            "getLog().debug(\"# collectUsedClassesFromProcessors()\");\n" +
            "return Optional.ofNullable(project.getPlugin(\"org.bsc.maven:maven-processor-plugin\"))\n" +
            "    .map(plugin -> plugin.getExecutionsAsMap().get(\"process\"))\n" +
            "    .map(exec -> exec.getConfiguration())\n" +
            "    .map(config -> {\n" +
            "      try {\n" +
            "        Object processors = config.getClass().getMethod(\"getChild\", String.class).invoke(config, \"processors\");\n" +
            "        if (processors == null) {\n" +
            "          return java.util.Collections.<String>emptySet();\n" +
            "        }\n" +
            "        Object[] children = (Object[]) processors.getClass().getMethod(\"getChildren\").invoke(processors);\n" +
            "        return java.util.Arrays.stream(children).map(c -> {\n" +
            "          try {\n" +
            "            return (String) c.getClass().getMethod(\"getValue\").invoke(c);\n" +
            "          } catch (Exception e) {\n" +
            "            return null;\n" +
            "          }\n" +
            "        }).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());\n" +
            "      } catch (Exception e) {\n" +
            "        return java.util.Collections.<String>emptySet();\n" +
            "      }\n" +
            "    })\n" +
            "    .orElse(of())"
        );

        CtBlock<?> block1 = launcher.getFactory().Core().createBlock();
        block1.addStatement(snippet1);
        method1.setBody(block1);

        CtMethod<?> method2 = clazz.getMethodsByName("buildModel").get(0);
        CtCodeSnippetStatement snippet2 = launcher.getFactory().Code().createCodeSnippetStatement(
            "File pomFile = new File(project.getBasedir().getAbsolutePath() + File.separator + \"pom.xml\");\n" +
            "Model model = null;\n" +
            "FileReader reader;\n" +
            "MavenXpp3Reader mavenReader = new MavenXpp3Reader();\n" +
            "try {\n" +
            "  reader = new FileReader(pomFile);\n" +
            "  model = (Model) mavenReader.getClass().getMethod(\"read\", java.io.Reader.class).invoke(mavenReader, reader);\n" +
            "  model.setPomFile(pomFile);\n" +
            "} catch (Exception ex) {\n" +
            "  getLog().error(\"Unable to build the maven project.\");\n" +
            "  throw new RuntimeException(ex);\n" +
            "}\n" +
            "return model"
        );

        CtBlock<?> block2 = launcher.getFactory().Core().createBlock();
        block2.addStatement(snippet2);
        method2.setBody(block2);

        launcher.prettyprint();
        
        // Remove the import from the file using basic string manipulation to avoid breaking imports
        java.io.File file = new java.io.File("/workspace/depclean/depclean-maven-plugin/src/main/java/se/kth/depclean/wrapper/MavenDependencyManager.java");
        String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
        content = content.replace("import org.codehaus.plexus.util.xml.Xpp3Dom;\n", "");
        
        // Fix any formatting issues introduced by Sniper
        content = content.replace("import lombok.AllArgsConstructor; import lombok.SneakyThrows;", "import lombok.AllArgsConstructor;\nimport lombok.SneakyThrows;");
        
        // Let's just fix the indentation by completely replacing the method body content for both methods
        String properMethod1 = "  public Set<String> collectUsedClassesFromProcessors() {\n" +
            "    getLog().debug(\"# collectUsedClassesFromProcessors()\");\n" +
            "    return Optional.ofNullable(project.getPlugin(\"org.bsc.maven:maven-processor-plugin\"))\n" +
            "        .map(plugin -> plugin.getExecutionsAsMap().get(\"process\"))\n" +
            "        .map(exec -> exec.getConfiguration())\n" +
            "        .map(config -> {\n" +
            "          try {\n" +
            "            Object processors = config.getClass().getMethod(\"getChild\", String.class).invoke(config, \"processors\");\n" +
            "            if (processors == null) {\n" +
            "              return java.util.Collections.<String>emptySet();\n" +
            "            }\n" +
            "            Object[] children = (Object[]) processors.getClass().getMethod(\"getChildren\").invoke(processors);\n" +
            "            return java.util.Arrays.stream(children).map(c -> {\n" +
            "              try {\n" +
            "                return (String) c.getClass().getMethod(\"getValue\").invoke(c);\n" +
            "              } catch (Exception e) {\n" +
            "                return null;\n" +
            "              }\n" +
            "            }).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());\n" +
            "          } catch (Exception e) {\n" +
            "            return java.util.Collections.<String>emptySet();\n" +
            "          }\n" +
            "        })\n" +
            "        .orElse(of());\n" +
            "  }";

        // We can just use a regex to replace the method `collectUsedClassesFromProcessors`
        content = content.replaceAll("(?s)  public Set<String> collectUsedClassesFromProcessors\(\) \{.*?\n  \}", java.util.regex.Matcher.quoteReplacement(properMethod1));

        String properMethod2 = "  private Model buildModel(MavenProject project) {\n" +
            "    File pomFile = new File(project.getBasedir().getAbsolutePath() + File.separator + \"pom.xml\");\n" +
            "    Model model = null;\n" +
            "    FileReader reader;\n" +
            "    MavenXpp3Reader mavenReader = new MavenXpp3Reader();\n" +
            "    try {\n" +
            "      reader = new FileReader(pomFile);\n" +
            "      model = (Model) mavenReader.getClass().getMethod(\"read\", java.io.Reader.class).invoke(mavenReader, reader);\n" +
            "      model.setPomFile(pomFile);\n" +
            "    } catch (Exception ex) {\n" +
            "      getLog().error(\"Unable to build the maven project.\");\n" +
            "      throw new RuntimeException(ex);\n" +
            "    }\n" +
            "    return model;\n" +
            "  }";

        content = content.replaceAll("(?s)  private Model buildModel\(MavenProject project\) \{.*?\n  \}", java.util.regex.Matcher.quoteReplacement(properMethod2));

        java.nio.file.Files.write(file.toPath(), content.getBytes());
    }
}