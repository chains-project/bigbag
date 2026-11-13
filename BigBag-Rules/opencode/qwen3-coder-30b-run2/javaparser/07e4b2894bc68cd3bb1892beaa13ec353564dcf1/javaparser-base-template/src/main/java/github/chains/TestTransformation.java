package github.chains;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class TestTransformation {
    public static void main(String[] args) {
        // Create a test Java file to demonstrate the transformation
        String testContent = 
            "public class TestExample {\n" +
            "    public void testMethod() {\n" +
            "        // This would be transformed\n" +
            "        oldMethod(\"param1\", \"param2\");\n" +
            "        processString(\"hello\");\n" +
            "    }\n" +
            "}\n";
            
        try {
            File testFile = new File("/tmp/TestExample.java");
            try (FileWriter writer = new FileWriter(testFile)) {
                writer.write(testContent);
            }
            System.out.println("Created test file: " + testFile.getAbsolutePath());
            System.out.println("This demonstrates what the transformation would process.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}