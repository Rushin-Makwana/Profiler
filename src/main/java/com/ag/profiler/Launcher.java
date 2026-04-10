package com.ag.profiler;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class Launcher {

    public static void main(String[] args) {
        String customAgent = "../../../AGInstrument/target/profiler-agent-1.0-SNAPSHOT.jar";
        String joularAgent = "../../../joularjx/target/joularjx-3.1.0.jar";
        String outputDir = "../Ptidej/ptidej-Ptidej/POM/Output";
        int NumOfRuns = 20;

        try {
            System.out.println("=== Phase 1: Running Custom Profiler ===");
            runProcess(Arrays.asList(
                    "mvn", "test",
                    "-Dmaven.repo.local=/Users/mac/.m2/repository",
                    "-Dtest=TestPOM",
                    "-DargLine='-javaagent:" + customAgent + "'"));
            System.out.println("[Launcher] Custom Profiler Run Finished.");

            System.out.println("\n=== Phase 2: Running JoularJX ===");
            for (int i = 0; i < NumOfRuns; i++) {
                System.out.println("[Launcher] JoularJX Run " + (i + 1) + "/" + NumOfRuns);
                runProcess(Arrays.asList(
                        "mvn", "test",
                        "-Dmaven.repo.local=/Users/mac/.m2/repository",
                        "-Dtest=TestPOM",
                        "-DargLine='-javaagent:" + joularAgent + " -Djoular.config:config.properties'"));

                // Rename JoularJX output file to prevent overwriting in the next run
                File JoularFile = new File(outputDir +
                        "/joularJX-123-all-methods-energy.csv");
                if (JoularFile.exists()) {
                    File renamedFile = new File(outputDir + "/" + (i + 1) +
                            "-joularJX-123-all-methods-energy.csv");
                    JoularFile.renameTo(renamedFile);
                }

                System.out.println("[Launcher] JoularJX Run Finished.");
            }

            System.out.println("\n=== Phase 3: Merging Results ===");
            // Both processes have fully exited, all shutdown hooks executed safely!
            String profilerCsv = outputDir + "/profiler_results.csv";
            EnergyMerger.merge(profilerCsv, outputDir);

            System.out.println("=== ALL PROFILING COMPLETE ===");

        } catch (

        Exception e) {
            e.printStackTrace();
        }
    }

    private static void runProcess(List<String> command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder();

        // Fix the PATH environment variable directly for Eclipse!
        // We append the directory where 'mvn' is installed so the shell can find it.
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

        // This inherits the IO so you can see the application's output in your console
        builder.inheritIO();

        // Set the working directory to the project root so `mvn test` finds `pom.xml`
        File newCurrentWorkingDirectory = new File("../Ptidej/ptidej-Ptidej/POM/");
        builder.directory(newCurrentWorkingDirectory);

        // Start the JVM
        Process process = builder.start();

        // Wait for the JVM to fully finish (including its shutdown hooks)
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Process exited with error code: " + exitCode);
        }
    }
}
