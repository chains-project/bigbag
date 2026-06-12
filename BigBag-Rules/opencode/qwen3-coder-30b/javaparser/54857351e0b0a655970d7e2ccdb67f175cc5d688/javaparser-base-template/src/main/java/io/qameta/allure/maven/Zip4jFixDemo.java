package io.qameta.allure.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Demo class showing how to use the Zip4jBreakingChangeFixer
 */
public class Zip4jFixDemo {
    
    public static void main(String[] args) throws IOException {
        // Create a temporary test file with the problematic import
        String testCode = "package test;\n" +
                "\n" +
                "import net.lingala.zip4j.core.ZipFile;\n" +
                "import net.lingala.zip4j.core.AESDecrypter;\n" +
                "\n" +
                "public class TestClass {\n" +
                "    public void test() {\n" +
                "        ZipFile zipFile = new ZipFile();\n" +
                "    }\n" +
                "}\n";
        
        Path tempFile = Paths.get("test_temp.java");
        Files.write(tempFile, testCode.getBytes());
        
        System.out.println("Created test file with problematic imports:");
        System.out.println(Files.readString(tempFile));
        
        // Apply the transformation
        Zip4jBreakingChangeFixer.main(new String[]{tempFile.getParent().toString()});
        
        System.out.println("\nAfter transformation:");
        System.out.println(Files.readString(tempFile));
        
        // Clean up
        Files.delete(tempFile);
    }
}