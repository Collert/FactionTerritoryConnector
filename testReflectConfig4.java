import java.net.URL;
import java.net.URLClassLoader;
import java.io.File;
import java.lang.reflect.Method;

public class testReflectConfig4 {
    public static void main(String[] args) throws Exception {
        File file = new File("libs/open-parties-and-claims-forge-1.20.1-0.25.10.jar");
        URL[] urls = { file.toURI().toURL() };
        URLClassLoader cl = new URLClassLoader(urls);
        System.out.println("Looking for PlayerClaimInfo...");
        Class<?> clazz = cl.loadClass("xaero.pac.common.claims.player.PlayerClaimInfo");
        for (Method m : clazz.getDeclaredMethods()) {
             System.out.println(m.getName() + " : " + m.getReturnType().getName());
        }
    }
}
