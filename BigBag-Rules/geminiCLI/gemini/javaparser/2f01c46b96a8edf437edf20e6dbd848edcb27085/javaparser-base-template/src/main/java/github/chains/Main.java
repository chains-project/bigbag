package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        File file = new File("/workspace/depclean/depclean-maven-plugin/src/main/java/se/kth/depclean/wrapper/MavenDependencyManager.java");
        CompilationUnit cu = StaticJavaParser.parse(file);
        
        LexicalPreservingPrinter.setup(cu);

        cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("collectUsedClassesFromProcessors")).ifPresent(m -> {
            BlockStmt body = StaticJavaParser.parseBlock(
                "{\n" +
                "  getLog().debug(\"# collectUsedClassesFromProcessors()\");\n" +
                "  return Optional.ofNullable(project.getPlugin(\"org.bsc.maven:maven-processor-plugin\"))\n" +
                "      .map(plugin -> plugin.getExecutionsAsMap().get(\"process\"))\n" +
                "      .map(exec -> exec.getConfiguration())\n" +
                "      .map(config -> {\n" +
                "        try {\n" +
                "          Object processors = config.getClass()\n" +
                "              .getMethod(\"getChild\", String.class)\n" +
                "              .invoke(config, \"processors\");\n" +
                "          if (processors == null) {\n" +
                "            return null;\n" +
                "          }\n" +
                "          Object[] children = (Object[]) processors.getClass()\n" +
                "              .getMethod(\"getChildren\")\n" +
                "              .invoke(processors);\n" +
                "          return Arrays.stream(children).map(c -> {\n" +
                "            try {\n" +
                "              return (String) c.getClass().getMethod(\"getValue\").invoke(c);\n" +
                "            } catch (Exception e) {\n" +
                "              return null;\n" +
                "            }\n" +
                "          }).collect(Collectors.toSet());\n" +
                "        } catch (Exception e) {\n" +
                "          return null;\n" +
                "        }\n" +
                "      })\n" +
                "      .orElse(of());\n" +
                "}"
            );
            m.setBody(body);
        });

        cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("buildModel")).ifPresent(m -> {
            BlockStmt body = StaticJavaParser.parseBlock(
                "{\n" +
                "  File pomFile = new File(project.getBasedir().getAbsolutePath() + File.separator + \"pom.xml\");\n" +
                "  final Model model;\n" +
                "  FileReader reader;\n" +
                "  MavenXpp3Reader mavenReader = new MavenXpp3Reader();\n" +
                "  try {\n" +
                "    reader = new FileReader(pomFile);\n" +
                "    model = (Model) mavenReader.getClass().getMethod(\"read\", java.io.Reader.class).invoke(mavenReader, reader);\n" +
                "    model.setPomFile(pomFile);\n" +
                "  } catch (Exception ex) {\n" +
                "    getLog().error(\"Unable to build the maven project.\");\n" +
                "    throw new RuntimeException(ex);\n" +
                "  }\n" +
                "  return model;\n" +
                "}"
            );
            m.setBody(body);
        });

        String source = LexicalPreservingPrinter.print(cu);
        source = source.replace("import org.codehaus.plexus.util.xml.Xpp3Dom;\n", "");
        
        Files.write(Paths.get(file.getAbsolutePath()), source.getBytes());
        System.out.println("Transformed with LexicalPreservingPrinter!");
    }
}
