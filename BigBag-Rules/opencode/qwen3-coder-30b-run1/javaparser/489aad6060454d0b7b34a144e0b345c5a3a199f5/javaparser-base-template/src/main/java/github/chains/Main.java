package github.chains;

import java.io.*;

public class Main {
    public static void main(String[] args) throws IOException {
        // Directly replace the method in the file
        String inputFile = "/workspace/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java";
        String outputFile = "/workspace/nem-fixed/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java";
        
        // Create output directory
        new File("/workspace/nem-fixed/nis/src/main/java/org/nem/specific/deploy/appconfig/").mkdirs();
        
        // Read the original file
        String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(inputFile)));
        
        // Replace the flyway method with fixed version
        String fixedContent = content.replace(
            "	@Bean(initMethod = \"migrate\")\n" +
            "	public Flyway flyway() throws IOException {\n" +
            "		final Properties prop = new Properties();\n" +
            "		prop.load(NisAppConfig.class.getClassLoader().getResourceAsStream(\"db.properties\"));\n" +
            "\n" +
            "		final org.flywaydb.core.Flyway flyway = new Flyway();\n" +
            "		flyway.setDataSource(this.dataSource());\n" +
            "		flyway.setClassLoader(NisAppConfig.class.getClassLoader());\n" +
            "		flyway.setLocations(prop.getProperty(\"flyway.locations\"));\n" +
            "		flyway.setValidateOnMigrate(Boolean.valueOf(prop.getProperty(\"flyway.validate\")));\n" +
            "		return flyway;\n" +
            "	}",
            "	@Bean(initMethod = \"migrate\")\n" +
            "	public Flyway flyway() throws IOException {\n" +
            "		final Properties prop = new Properties();\n" +
            "		prop.load(NisAppConfig.class.getClassLoader().getResourceAsStream(\"db.properties\"));\n" +
            "\n" +
            "		final org.flywaydb.core.api.configuration.FluentConfiguration config = new org.flywaydb.core.api.configuration.FluentConfiguration();\n" +
            "		config.dataSource(this.dataSource());\n" +
            "		config.classLoader(NisAppConfig.class.getClassLoader());\n" +
            "		config.locations(prop.getProperty(\"flyway.locations\"));\n" +
            "		config.validateOnMigrate(Boolean.valueOf(prop.getProperty(\"flyway.validate\")));\n" +
            "		return new Flyway(config.load());\n" +
            "	}"
        );
        
        // Write the fixed content to the output file
        try (PrintWriter out = new PrintWriter(outputFile)) {
            out.println(fixedContent);
        }
        
        System.out.println("Transformation complete. Fixed file written to: " + outputFile);
    }
}