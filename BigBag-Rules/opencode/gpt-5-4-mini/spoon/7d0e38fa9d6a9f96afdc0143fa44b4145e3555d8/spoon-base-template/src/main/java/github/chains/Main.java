package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
  private static final String LOCATION = "global";

  private static final Map<String, List<String>> OLD_CALLS = new LinkedHashMap<>();

  static {
    OLD_CALLS.put("managedZones", List.of("create", "delete", "get", "list", "patch", "update"));
    OLD_CALLS.put("resourceRecordSets", List.of("create", "delete", "get", "list", "patch"));
    OLD_CALLS.put("projects", List.of("get"));
    OLD_CALLS.put("changes", List.of("create", "get", "list"));
    OLD_CALLS.put("dnsKeys", List.of("get", "list"));
    OLD_CALLS.put("managedZoneOperations", List.of("get", "list"));
    OLD_CALLS.put("policies", List.of("create", "delete", "get", "list", "patch", "update"));
    OLD_CALLS.put("responsePolicies", List.of("create", "delete", "get", "list", "patch", "update"));
    OLD_CALLS.put(
        "responsePolicyRules", List.of("create", "delete", "get", "list", "patch", "update"));
  }

  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      throw new IllegalArgumentException("Usage: Main <input-src> <output-src>");
    }

    Path inputDir = Paths.get(args[0]);
    Path outputDir = Paths.get(args[1]);

    Files.createDirectories(outputDir);
    Files.walkFileTree(
        inputDir,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
              throws IOException {
            Path target = outputDir.resolve(inputDir.relativize(dir).toString());
            Files.createDirectories(target);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Path target = outputDir.resolve(inputDir.relativize(file).toString());
            if (file.toString().endsWith(".java")) {
              String source = Files.readString(file, StandardCharsets.UTF_8);
              Files.writeString(target, transform(source), StandardCharsets.UTF_8);
            } else {
              if (!Files.exists(target)) {
                Files.copy(file, target);
              }
            }
            return FileVisitResult.CONTINUE;
          }
        });
  }

  private static String transform(String source) {
    String transformed = source;
    for (Map.Entry<String, List<String>> entry : OLD_CALLS.entrySet()) {
      String resource = entry.getKey();
      for (String method : entry.getValue()) {
        transformed = addLocationArgument(transformed, resource, method);
      }
    }
    return transformed;
  }

  private static String addLocationArgument(String source, String resource, String method) {
    Pattern pattern =
        Pattern.compile(resource + "\\s*\\(\\s*\\)\\s*\\.\\s*" + method + "\\s*\\(", Pattern.DOTALL);
    StringBuilder out = new StringBuilder(source.length() + 128);
    int index = 0;
    while (true) {
      Matcher matcher = pattern.matcher(source);
      if (!matcher.find(index)) {
        out.append(source, index, source.length());
        return out.toString();
      }

      int openParen = matcher.end() - 1;
      int closeParen = findMatchingParen(source, openParen);
      if (closeParen < 0) {
        out.append(source, index, source.length());
        return out.toString();
      }

      String args = source.substring(openParen + 1, closeParen).trim();
      int argCount = args.isEmpty() ? 0 : countTopLevelCommas(args) + 1;
      if (needsLocationPrefix(resource, method, argCount)) {
        out.append(source, index, openParen + 1);
        out.append('"').append(LOCATION).append('"');
        if (!args.isEmpty()) {
          out.append(", ").append(source, openParen + 1, closeParen);
        }
        index = closeParen;
      } else {
        out.append(source, index, closeParen);
        index = closeParen;
      }
    }
  }

  private static boolean needsLocationPrefix(String resource, String method, int argCount) {
    if ("managedZones".equals(resource)) {
      return switch (method) {
        case "list" -> argCount == 1;
        case "create", "get", "delete" -> argCount == 2;
        case "patch", "update" -> argCount == 3;
        default -> false;
      };
    }
    if ("resourceRecordSets".equals(resource)) {
      return switch (method) {
        case "list" -> argCount == 2;
        case "create", "get" -> argCount == 2;
        case "delete" -> argCount == 2;
        case "patch" -> argCount == 3;
        default -> false;
      };
    }
    if ("projects".equals(resource)) {
      return method.equals("get") && argCount == 1;
    }
    if ("changes".equals(resource)) {
      return switch (method) {
        case "list" -> argCount == 2;
        case "get" -> argCount == 3;
        case "create" -> argCount == 3;
        default -> false;
      };
    }
    if ("dnsKeys".equals(resource) || "managedZoneOperations".equals(resource)) {
      return switch (method) {
        case "list" -> argCount == 2;
        case "get" -> argCount == 3;
        default -> false;
      };
    }
    if ("policies".equals(resource) || "responsePolicies".equals(resource)) {
      return switch (method) {
        case "list" -> argCount == 1;
        case "create", "get", "delete" -> argCount == 2;
        case "patch", "update" -> argCount == 3;
        default -> false;
      };
    }
    if ("responsePolicyRules".equals(resource)) {
      return switch (method) {
        case "list" -> argCount == 2;
        case "create", "get", "delete" -> argCount == 3;
        case "patch", "update" -> argCount == 4;
        default -> false;
      };
    }
    return false;
  }

  private static int findMatchingParen(String source, int openParen) {
    int depth = 0;
    boolean inString = false;
    boolean escaped = false;
    for (int i = openParen; i < source.length(); i++) {
      char c = source.charAt(i);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      }
      if (c == '"') {
        inString = true;
        continue;
      }
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  private static int countTopLevelCommas(String args) {
    int commas = 0;
    int depth = 0;
    boolean inString = false;
    boolean escaped = false;
    for (int i = 0; i < args.length(); i++) {
      char c = args.charAt(i);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      }
      if (c == '"') {
        inString = true;
      } else if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      } else if (c == ',' && depth == 0) {
        commas++;
      }
    }
    return commas;
  }
}
