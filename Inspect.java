import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.io.File;

public class Inspect {
    public static void main(String[] args) throws Exception {
        File file = new File("libs/open-parties-and-claims-forge-1.20.1-0.25.10.jar");
        URL[] urls = { file.toURI().toURL() };
        URLClassLoader cl = new URLClassLoader(urls);
        System.out.println("Looking for IPlayerPermissionSystemAPI...");
        Class<?> clazz = cl.loadClass("xaero.pac.common.server.player.permission.api.IPlayerPermissionSystemAPI");
        for (Method m : clazz.getDeclaredMethods()) {
            System.out.println(m.toString());
        }
        System.out.println("Looking for IClaimsManagerListenerAPI...");
        Class<?> clazz2 = cl.loadClass("xaero.pac.common.claims.tracker.api.IClaimsManagerListenerAPI");
        for (Method m : clazz2.getDeclaredMethods()) {
            System.out.println(m.toString());
        }
    }
}
