import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.io.File;

public class testReflectRecruits {
    public static void main(String[] args) throws Exception {
        File file = new File("libs/recruits-1.20.1-1.14.2.3.jar");
        URL[] urls = { file.toURI().toURL() };
        URLClassLoader cl = new URLClassLoader(urls);
        System.out.println("Looking for RecruitsClaimManager...");
        Class<?> clazz2 = cl.loadClass("com.talhanation.recruits.world.RecruitsClaimManager");
        for (Method m : clazz2.getDeclaredMethods()) {
             System.out.println(m.getName() + " : " + m.getReturnType().getName());
             for(Class<?> pt : m.getParameterTypes()) {
                  System.out.println("  -- param: " + pt.getName());
             }
        }
        
        System.out.println("\nLooking for RecruitsClaim constructor...");
        Class<?> clazz3 = cl.loadClass("com.talhanation.recruits.world.RecruitsClaim");
        for (Constructor<?> c : clazz3.getDeclaredConstructors()) {
             System.out.println(c.toString());
        }
        
        System.out.println("\nLooking for ClaimEvents...");
        Class<?> clazz4 = cl.loadClass("com.talhanation.recruits.ClaimEvents");
        for (java.lang.reflect.Field f : clazz4.getDeclaredFields()) {
             System.out.println(f.getName() + " : " + f.getType().getName());
        }
    }
}
