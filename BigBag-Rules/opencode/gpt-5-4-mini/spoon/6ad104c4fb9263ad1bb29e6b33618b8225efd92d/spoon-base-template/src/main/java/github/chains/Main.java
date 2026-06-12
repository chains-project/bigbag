package github.chains;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtImportKind;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

  private static final String OLD_TL_FATAL_ALERT = "org.bouncycastle.crypto.tls.TlsFatalAlert";
  private static final String NEW_EXCEPTION = "java.io.IOException";

  public static void main(String[] args) {
    if (args.length < 2) {
      throw new IllegalArgumentException("Expected <input-source-dir> <output-source-dir>");
    }

    Launcher launcher = new Launcher();
    launcher.addInputResource(args[0]);
    launcher.setSourceOutputDirectory(new File(args[1]));
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.buildModel();

    rewriteTlsAlerts(launcher);

    launcher.prettyprint();
  }

  private static void rewriteTlsAlerts(Launcher launcher) {
    List<CtConstructorCall> calls = launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class));

    for (CtConstructorCall<?> call : calls) {
      CtTypeReference<?> type = call.getType();
      if (type == null || !OLD_TL_FATAL_ALERT.equals(type.getQualifiedName())) {
        continue;
      }

      String message = extractAlertName(call.getArguments());
      CtConstructorCall replacement = launcher.getFactory().Core().createConstructorCall();
      replacement.setType(launcher.getFactory().Type().createReference(NEW_EXCEPTION));
      replacement.addArgument(stringLiteral(launcher, message));
      call.replace(replacement);
    }

    for (CtImport ctImport : launcher.getModel().getElements(new TypeFilter<>(CtImport.class))) {
      removeLegacyTlsImport(ctImport);
    }
  }

  private static String extractAlertName(List<CtExpression<?>> arguments) {
    if (arguments.isEmpty()) {
      return "bcprov tls alert";
    }

    CtExpression<?> first = arguments.get(0);
    if (first instanceof CtFieldAccess<?> fieldAccess) {
      if (fieldAccess.getVariable() != null && fieldAccess.getVariable().getSimpleName() != null) {
        return fieldAccess.getVariable().getSimpleName();
      }
    }

    if (first instanceof CtLiteral<?> literal && literal.getValue() != null) {
      return String.valueOf(literal.getValue());
    }

    return first.toString();
  }

  private static CtExpression<?> stringLiteral(Launcher launcher, String value) {
    return launcher.getFactory().Code().createLiteral(value);
  }

  private static void removeLegacyTlsImport(CtImport ctImport) {
    if (ctImport.getImportKind() != CtImportKind.TYPE) {
      return;
    }

    CtReference reference = ctImport.getReference();
    if (reference instanceof CtTypeReference<?> typeReference) {
      String qn = typeReference.getQualifiedName();
      if (qn != null && qn.startsWith("org.bouncycastle.crypto.tls.")) {
        ctImport.delete();
      }
    }
  }
}
