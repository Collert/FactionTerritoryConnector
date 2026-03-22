import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.io.File;

public class testReflectConfig2 {
    public static void main(String[] args) throws Exception {
        File file = new File("libs/open-parties-and-claims-forge-1.20.1-0.25.10.jar");
        URL[] urls = { file.toURI().toURL() };
        URLClassLoader cl = new URLClassLoader(urls);
        System.out.println("Looking for IPlayerConfigAPI...");
        Class<?> clazz2 = cl.loadClass("xaero.pac.common.server.player.config.api.IPlayerConfigAPI");
        for (Method m : clazz2.getDeclaredMethods()) {
             System.out.println(m.getName() + " : " + m.getReturnType().getName());
             for(Class<?> pt : m.getParameterTypes()) {
                  System.out.println("  -- param: " + pt.getName());
             }
        }
    }
}
