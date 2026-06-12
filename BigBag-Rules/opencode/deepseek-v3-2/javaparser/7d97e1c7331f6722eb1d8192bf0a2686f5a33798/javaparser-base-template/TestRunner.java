import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

public class TestRunner {
    public static void main(String[] args) throws Exception {
        // Create classpath with dependencies
        File[] dependencies = new File("target/dependency").listFiles();
        StringBuilder classpath = new StringBuilder("target/classes");
        if (dependencies != null) {
            for (File dep : dependencies) {
                classpath.append(File.pathSeparator).append(dep.getAbsolutePath());
            }
        }
        
        // Run the transformation
        String[] mainArgs = {"/workspace/jcabi-simpledb/src/test/java", "com.jcabi.aspects.Tv"};
        ClassLoader cl = new URLClassLoader(new URL[]{new File("target/classes").toURI().toURL()});
        Class<?> mainClass = cl.loadClass("github.chains.Main");
        mainClass.getMethod("main", String[].class).invoke(null, (Object) mainArgs);
    }
}
