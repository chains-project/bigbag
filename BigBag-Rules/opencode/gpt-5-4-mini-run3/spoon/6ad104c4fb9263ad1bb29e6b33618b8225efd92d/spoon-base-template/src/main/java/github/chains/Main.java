package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

  private static final Pattern IMPORT_TLS_FATAL_ALERT =
      Pattern.compile("^\\s*import\\s+org\\.bouncycastle\\.crypto\\.tls\\.TlsFatalAlert;\\s*$", Pattern.MULTILINE);
  private static final Pattern IMPORT_ALERT_DESCRIPTION =
      Pattern.compile("^\\s*import\\s+org\\.bouncycastle\\.crypto\\.tls\\.AlertDescription;\\s*$", Pattern.MULTILINE);
  private static final Pattern THROW_TLS_FATAL_ALERT = Pattern.compile(
      "throw\\s+new\\s+TlsFatalAlert\\s*\\(\\s*AlertDescription\\.([A-Za-z0-9_]+)\\s*\\)\\s*;",
      Pattern.MULTILINE);

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("Usage: Main <source-directory>");
    }

    Path root = Paths.get(args[0]).toAbsolutePath().normalize();
    if (!Files.isDirectory(root)) {
      throw new IllegalArgumentException("Source directory does not exist: " + root);
    }

    List<Path> files = new ArrayList<>();
    try (var stream = Files.walk(root)) {
      stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java")).forEach(files::add);
    }

    for (Path file : files) {
      String original = Files.readString(file, StandardCharsets.UTF_8);
      String updated = transform(original);
      if (!updated.equals(original)) {
        Files.writeString(file, updated, StandardCharsets.UTF_8);
      }
    }
  }

  private static String transform(String source) {
    String updated = IMPORT_TLS_FATAL_ALERT.matcher(source).replaceAll("");
    updated = IMPORT_ALERT_DESCRIPTION.matcher(updated).replaceAll("");

    Matcher matcher = THROW_TLS_FATAL_ALERT.matcher(updated);
    StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      String alert = matcher.group(1);
      String message = toMessage(alert);
      matcher.appendReplacement(buffer,
          Matcher.quoteReplacement("throw new java.io.IOException(\"" + escapeJava(message) + "\");"));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  private static String toMessage(String alert) {
    return alert.replace('_', ' ');
  }

  private static String escapeJava(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
