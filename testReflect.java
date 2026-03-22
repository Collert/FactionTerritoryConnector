import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.io.File;

public class testReflect {
    public static void main(String[] args) throws Exception {
        File file = new File("libs/open-parties-and-claims-forge-1.20.1-0.25.10.jar");
        URL[] urls = { file.toURI().toURL() };
        URLClassLoader cl = new URLClassLoader(urls);
        System.out.println("Looking for ServerPlayerClaimInfo...");
        Class<?> clazz = cl.loadClass("xaero.pac.common.server.claims.player.ServerPlayerClaimInfo");
        for (Method m : clazz.getDeclaredMethods()) {
            System.out.println(m.getName());
        }
    }
}
