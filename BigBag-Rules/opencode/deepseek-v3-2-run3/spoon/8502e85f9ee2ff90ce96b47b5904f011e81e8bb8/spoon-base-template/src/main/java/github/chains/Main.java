package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        System.out.println("Transforming Flyway API usage from no-arg constructor + setters to FluentConfiguration pattern");
        
        // Process the two files we know have Flyway usage
        List<String> files = Arrays.asList(
            "/workspace/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java",
            "/workspace/nem/nis/src/test/java/org/nem/nis/dao/TestConf.java"
        );
        
        for (String filePath : files) {
            transformFile(Paths.get(filePath));
        }
        
        System.out.println("Transformation completed successfully!");
    }
    
    private static void transformFile(Path filePath) throws IOException {
        System.out.println("Processing: " + filePath);
        String content = Files.readString(filePath);
        
        // Transform NisAppConfig.java
        if (filePath.toString().contains("NisAppConfig.java")) {
            content = content.replace(
                "final org.flywaydb.core.Flyway flyway = new Flyway();\n" +
                "\t\tflyway.setDataSource(this.dataSource());\n" +
                "\t\tflyway.setClassLoader(NisAppConfig.class.getClassLoader());\n" +
                "\t\tflyway.setLocations(prop.getProperty(\"flyway.locations\"));\n" +
                "\t\tflyway.setValidateOnMigrate(Boolean.valueOf(prop.getProperty(\"flyway.validate\")));",
                
                "final org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()\n" +
                "\t\t.dataSource(this.dataSource())\n" +
                "\t\t.classLoader(NisAppConfig.class.getClassLoader())\n" +
                "\t\t.locations(prop.getProperty(\"flyway.locations\"))\n" +
                "\t\t.validateOnMigrate(Boolean.valueOf(prop.getProperty(\"flyway.validate\")))\n" +
                "\t\t.load();"
            );
        }
        
        // Transform TestConf.java
        if (filePath.toString().contains("TestConf.java")) {
            content = content.replace(
                "final Flyway flyway = new Flyway();\n" +
                "\t\tflyway.setDataSource(this.dataSource());\n" +
                "\t\tflyway.setLocations(\"db/h2\");",
                
                "final Flyway flyway = org.flywaydb.core.Flyway.configure()\n" +
                "\t\t.dataSource(this.dataSource())\n" +
                "\t\t.locations(\"db/h2\")\n" +
                "\t\t.load();"
            );
        }
        
        Files.writeString(filePath, content);
        System.out.println("Transformed: " + filePath);
    }
}