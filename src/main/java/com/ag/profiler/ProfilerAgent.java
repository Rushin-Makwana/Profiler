package com.ag.profiler;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

public class ProfilerAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[ProfilerAgent] Starting Profiler Agent...");

        try {
            // Append agent jar to bootstrap classpath so bootstrap classes (like java.util.*)
            // can access our Advice and Data classes when they are instrumented.
            String jarPath = ProfilerAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            File jarFile = new File(jarPath);
            inst.appendToBootstrapClassLoaderSearch(new JarFile(jarFile));

            // Enable Byte Buddy experimental support to allow instrumenting classes in newer JVMs (like Java 25+)
            System.setProperty("com.ag.profiler.bytebuddy.experimental", "true");

            // Use reflection to load AgentInstaller to prevent premature loading of Byte Buddy
            // classes by the application class loader before they are added to the bootstrap path.
            Class<?> installerClass = Class.forName("com.ag.profiler.AgentInstaller");
            installerClass.getMethod("install", String.class, Instrumentation.class, String.class).invoke(null, agentArgs, inst, jarPath);
        } catch (Exception e) {
            System.err.println("[ProfilerAgent] Failed to initialize agent.");
            e.printStackTrace();
        }
    }
}
