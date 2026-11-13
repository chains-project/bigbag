import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

public class RunTransformation {
    public static void main(String[] args) throws Exception {
        // Get classpath
        String classpath = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get("/tmp/classpath.txt"))).trim();
        
        // Add Spoon dependencies
        String spoonClasspath = System.getProperty("java.class.path");
        
        // Run Main with proper classpath
        String[] mainArgs = {"/workspace/docker-adapter/src/test/java"};
        
        // Use reflection to avoid classpath issues
        String[] paths = (classpath + ":" + spoonClasspath).split(":");
        URL[] urls = new URL[paths.length];
        int i = 0;
        for (String path : paths) {
            if (!path.isEmpty()) {
                try {
                    urls[i++] = new File(path).toURI().toURL();
                } catch (MalformedURLException e) {
                    System.err.println("Invalid path: " + path);
                }
            }
        }
        
        ClassLoader cl = new java.net.URLClassLoader(
            java.util.Arrays.copyOf(urls, i),
            ClassLoader.getSystemClassLoader());
        
        Class<?> mainClass = cl.loadClass("github.chains.Main");
        java.lang.reflect.Method mainMethod = mainClass.getMethod("main", String[].class);
        mainMethod.invoke(null, (Object) mainArgs);
    }
}
