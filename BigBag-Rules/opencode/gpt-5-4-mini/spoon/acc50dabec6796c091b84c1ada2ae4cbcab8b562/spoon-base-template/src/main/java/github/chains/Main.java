package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class Main {
  private static final String OLD_TYPE =
      "org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter";
  private static final String NEW_TYPE =
      "org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter";

  private Main() {}

  public static void main(String[] args) {
    if (args.length < 2) {
      throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-dir>");
    }

    Path inputDir = Paths.get(args[0]);
    Path outputDir = Paths.get(args[1]);

    Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.addInputResource(inputDir.toString());
    launcher.setSourceOutputDirectory(outputDir.toFile());
    launcher.buildModel();

    CtTypeReference<?> replacement = launcher.getFactory().Type().createReference(NEW_TYPE);
    CtModel model = launcher.getModel();

    for (CtTypeReference<?> ref : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
      if (OLD_TYPE.equals(ref.getQualifiedName())) {
        ref.replace(replacement.clone());
      }
    }

    launcher.prettyprint();
  }
}
