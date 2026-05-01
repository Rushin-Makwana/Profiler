package com.ag.profiler;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.io.File;

public class AgentInstaller {

    public static void install(String agentArgs, Instrumentation inst, String jarPath) {
        // This is an ALLOW-LIST of packages to instrument.
        // We MUST include "ca" to instrument your TestWorkload classes.
        String[] packages = {
                "pom", "padl", "java.util", "java.io", "java.lang", "java.net", "jdk",
                "java.internal", "java.reflection", "org.apache.commons", "org.slf4j",
                "java.nio", "java.math", "java.awt", "java.security", "util", "org.junit",
                "org.apache.logging", "sun", "org.apache.maven", "junit", "com",
                "java.sql", "org.springframework", "javax", "ca"
        };

        if (agentArgs != null && !agentArgs.isEmpty()) {
            packages = agentArgs.split(",");
        }

        System.out.println("[ProfilerAgent] Configured to instrument packages: " + Arrays.toString(packages));

        // Add shutdown hook to write CSV
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[ProfilerAgent] JVM shutting down. Writing statistics to CSV...");
            
            // Ensure Output directory exists relative to current working directory (SPECTRA/)
            File outputDir = new File("Output");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            String profilerCsv = "Output/profiler_results.csv";
            CallStatistics.writeToCsv(profilerCsv);

            // Automatically merge with any generated JoularJX energy CSV files
            System.out.println("[ProfilerAgent] Triggering automatic CSV merge...");
            EnergyMerger.merge(profilerCsv, "Output");
        }));

        // Matcher for packages exactly.
        ElementMatcher.Junction<TypeDescription> typeMatcher = ElementMatchers.none();
        for (String pkg : packages) {
            typeMatcher = typeMatcher.or(ElementMatchers.nameStartsWith(pkg));
        }

        net.bytebuddy.dynamic.ClassFileLocator agentLocator;
        try {
            java.io.File file = new java.io.File(jarPath);
            agentLocator = new net.bytebuddy.dynamic.ClassFileLocator.ForJarFile(new java.util.jar.JarFile(file));
        } catch (Exception e) {
            System.err.println("Failed to create ClassFileLocator for ProfilerAdvice");
            e.printStackTrace();
            return;
        }

        ElementMatcher.Junction<net.bytebuddy.description.type.TypeDescription> internalIgnores = ElementMatchers
                .nameStartsWith("com.ag.profiler.")
                .or(ElementMatchers.nameStartsWith("net.bytebuddy."))
                .or(ElementMatchers.nameStartsWith("jdk.internal."));

        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(new AgentBuilder.LocationStrategy() {
                    @Override
                    public net.bytebuddy.dynamic.ClassFileLocator classFileLocator(ClassLoader classLoader,
                            net.bytebuddy.utility.JavaModule module) {
                        return new net.bytebuddy.dynamic.ClassFileLocator.Compound(
                                agentLocator,
                                AgentBuilder.LocationStrategy.ForClassLoader.STRONG.classFileLocator(classLoader,
                                        module),
                                net.bytebuddy.dynamic.ClassFileLocator.ForModule.ofBootLayer());
                    }
                })
                .ignore(internalIgnores)
                .type(typeMatcher)
                .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                    net.bytebuddy.dynamic.ClassFileLocator targetLocator = AgentBuilder.LocationStrategy.ForClassLoader.STRONG
                            .classFileLocator(classLoader, module);
                    net.bytebuddy.dynamic.ClassFileLocator finalLocator = new net.bytebuddy.dynamic.ClassFileLocator.Compound(
                            agentLocator, targetLocator);
                    return builder.visit(Advice.to(ProfilerAdvice.class, finalLocator)
                            .on(ElementMatchers.isMethod().and(ElementMatchers.not(ElementMatchers.isAbstract()))));
                })
                .installOn(inst);
    }
}