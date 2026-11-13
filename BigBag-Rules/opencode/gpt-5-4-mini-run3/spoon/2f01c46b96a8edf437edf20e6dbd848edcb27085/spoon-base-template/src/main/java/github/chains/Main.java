package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;

public class Main {

  private static final String OLD_TYPE = "org.codehaus.plexus.util.xml.Xpp3Dom";
  private static final String NEW_TYPE = "org.codehaus.plexus.configuration.PlexusConfiguration";

  public static void main(String[] args) {
    if (args.length < 1 || args.length > 2) {
      throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
    }

    Path inputDir = Paths.get(args[0]);
    Path outputDir = args.length == 2 ? Paths.get(args[1]) : inputDir.resolveSibling(inputDir.getFileName() + "-fixed");

    Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.addInputResource(inputDir.toString());
    launcher.setSourceOutputDirectory(outputDir.toFile());
    launcher.addProcessor(new AbstractProcessor<CtTypeReference<?>>() {
      @Override
      public boolean isToBeProcessed(CtTypeReference<?> candidate) {
        return candidate != null && OLD_TYPE.equals(candidate.getQualifiedName());
      }

      @Override
      public void process(CtTypeReference<?> reference) {
        reference.replace(getFactory().Type().createReference(NEW_TYPE));
      }
    });
    launcher.run();
    launcher.prettyprint();
  }
}
