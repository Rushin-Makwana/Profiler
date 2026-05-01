package com.ag.profiler;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Launcher {

    public static void main(String[] args) {
        // Using Absolute Paths for maximum reliability
        String customAgent = "/Users/mac/Documents/RA/AGInstrument/target/profiler-agent-1.0-SNAPSHOT.jar";
        String joularAgent = "/Users/mac/Documents/RA/joularjx/target/joularjx-3.1.0.jar";
        String configPath = "/Users/mac/Documents/RA/SPECTRA/src/main/resources/config.properties";
        String outputDir = "/Users/mac/Documents/RA/SPECTRA";
        int NumOfRuns = 1;

        // Module opening flags for Java 25 compatibility
        String moduleFlags = "--add-opens java.base/java.lang=ALL-UNNAMED " +
                "--add-opens java.base/java.util=ALL-UNNAMED " +
                "--add-opens java.base/java.lang.reflect=ALL-UNNAMED " +
                "--add-opens java.base/java.io=ALL-UNNAMED " +
                "--add-opens java.base/java.nio=ALL-UNNAMED " +
                "--add-opens java.base/sun.nio.ch=ALL-UNNAMED " +
                "--add-opens java.base/sun.security.action=ALL-UNNAMED " +
                "--add-opens java.base/jdk.internal.misc=ALL-UNNAMED";

        try {
            System.out.println("=== Phase 1: Running Custom Profiler ===");

            runProcess(Arrays.asList(
                    "mvn", "test",
                    "-Dmaven.repo.local=/Users/mac/.m2/repository",
                    "-Dtest=TestWorkload",
                    "-DargLine=\"-javaagent:" + customAgent + " " + moduleFlags + "\""));
            System.out.println("[Launcher] Custom Profiler Run Finished.");

            System.out.println("\n=== Phase 2: Running JoularJX ===");
            for (int i = 0; i < NumOfRuns; i++) {
                System.out.println("[Launcher] JoularJX Run " + (i + 1) + "/" + NumOfRuns);

                String joularArgLine = String.format("-javaagent:%s -Xbootclasspath/a:%s -Djoularjx.config=%s %s",
                        joularAgent, joularAgent, configPath, moduleFlags);

                runProcess(Arrays.asList(
                        "mvn", "test",
                        "-Dmaven.repo.local=/Users/mac/.m2/repository",
                        "-Dtest=TestWorkload",
                        "-DargLine=\"" + joularArgLine + "\""));

                // Renaming logic: JoularJX 3.1.0 with appId 123 saves directly to Output/
                File JoularFile = new File(outputDir + "/Output/joularJX-123-all-methods-energy.csv");
                if (JoularFile.exists()) {
                    File renamedFile = new File(
                            outputDir + "/Output/" + (i + 1) + "-joularJX-123-all-methods-energy.csv");
                    JoularFile.renameTo(renamedFile);
                    System.out.println("[Launcher] Successfully moved results to: " + renamedFile.getAbsolutePath());
                } else {
                    System.err.println("[Launcher] WARNING: JoularJX CSV not found at " + JoularFile.getAbsolutePath());
                }

                System.out.println("[Launcher] JoularJX Run Finished.");
            }

            System.out.println("\n=== Phase 3: Merging Results ===");
            String profilerCsv = outputDir + "/Output/profiler_results.csv";
            EnergyMerger.merge(profilerCsv, outputDir + "/Output");

            System.out.println("=== ALL PROFILING COMPLETE ===");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void runProcess(List<String> command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder();

        java.util.Map<String, String> env = builder.environment();
        String currentPath = env.getOrDefault("PATH", "");
        env.put("PATH", currentPath + ":/Users/mac/downloads/apache-maven-3.9.9/bin");

        // Execute via bash on Mac/Linux
        if (System.getProperty("os.name").toLowerCase().contains("mac")
                || System.getProperty("os.name").toLowerCase().contains("nix")) {
            String fullCommand = String.join(" ", command);
            builder.command("bash", "-c", fullCommand);
        } else {
            builder.command(command);
        }

        builder.inheritIO();
        builder.directory(new File("/Users/mac/Documents/RA/SPECTRA/"));

        Process process = builder.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Process exited with error code: " + exitCode);
        }
    }
}
