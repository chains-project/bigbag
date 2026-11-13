package io.qameta.allure.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Test the zip4j fixer on a sample file
 */
public class TestZip4jFixer {
    public static void main(String[] args) throws IOException {
        // Create a test file with problematic imports
        String testCode = "package test;\n" +
                "\n" +
                "import net.lingala.zip4j.core.ZipFile;\n" +
                "import net.lingala.zip4j.core.AESDecrypter;\n" +
                "import net.lingala.zip4j.core.StandardDecrypter;\n" +
                "\n" +
                "public class TestClass {\n" +
                "    public void test() {\n" +
                "        // This should be fixed\n" +
                "    }\n" +
                "}\n";
        
        Path tempFile = Paths.get("test_file.java");
        Files.write(tempFile, testCode.getBytes());
        
        System.out.println("Original file content:");
        System.out.println(Files.readString(tempFile));
        
        // Apply our fixer
        Zip4jBreakingChangeFixer.main(new String[]{"/workspace/javaparser-base-template"});
        
        System.out.println("\nAfter fix:");
        System.out.println(Files.readString(tempFile));
        
        // Clean up
        Files.delete(tempFile);
        System.out.println("Test completed.");
    }
}