package com.ag.profiler;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class EnergyMerger {

    public static void merge(String profilerCsvPath, String joularDir) {
        System.out.println("[EnergyMerger] Starting post-process merge of Profiler and JoularJX data...");

        File profilerFile = new File(profilerCsvPath);
        if (!profilerFile.exists()) {
            System.err.println("[EnergyMerger] Profiler CSV not found at " + profilerCsvPath);
            return;
        }

        Map<String, ProfilerEntry> profilerData = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(profilerFile))) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) {
                    first = false;
                    continue; // skip header
                }

                // Format: "MethodSignature",Invocations,TotalTime
                int firstComma = line.indexOf("\",");
                if (firstComma > 0) {
                    String sig = normalizeSignature(line.substring(1, firstComma));
                    String[] parts = line.substring(firstComma + 2).split(",");
                    if (parts.length >= 2) {
                        profilerData.put(sig, new ProfilerEntry(parts[0], parts[1]));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[EnergyMerger] Failed to read profiler CSV: " + e.getMessage());
        }

        System.out.println("[EnergyMerger] Loaded " + profilerData.size() + " methods from " + profilerCsvPath);

        // Find all JoularJX CSV files in the dir
        Map<String, JoularEntry> joularData = new HashMap<>();
        File dir = new File(joularDir);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.contains("joularJX-"));
            if (files != null && files.length > 0) {
                for (File jFile : files) {
                    processJoularFile(jFile, joularData);
                }
            } else {
                System.out.println("[EnergyMerger] No matching JoularJX CSV files found in " + joularDir);
                return;
            }
        } else {
            System.out.println("[EnergyMerger] Invalid JoularJX directory or not found: " + joularDir);
            return;
        }
        if (joularData.isEmpty()) {
            System.out.println("[EnergyMerger] No JoularJX energy data found in " + joularDir + " to merge.");
            return;
        }

        // Generate the requested standalone average Joular energy file
        String avgJoularFilename = "../Ptidej/ptidej-Ptidej/POM/Results/average_joular_energy.csv";
        try (PrintWriter pw = new PrintWriter(new FileWriter(avgJoularFilename))) {
            pw.println("Method Signature,Average Energy (J)");
            for (Map.Entry<String, JoularEntry> entry : joularData.entrySet()) {
                pw.printf("\"%s\",%.5f%n", entry.getKey(), entry.getValue().getAverageEnergy());
            }
            System.out.println("[EnergyMerger] Saved standalone average energy data to " + avgJoularFilename);
        } catch (Exception e) {
            System.err.println("[EnergyMerger] Failed to write intermediate average CSV: " + e.getMessage());
        }

        // Inner join both
        Set<String> intersection = new HashSet<>(profilerData.keySet());
        intersection.retainAll(joularData.keySet());

        System.out.println("[EnergyMerger] Found " + intersection.size() + " correlated methods. Writing output...");

        String outputFilename = "../Ptidej/ptidej-Ptidej/POM/Results/merged_profiling_energy_results.csv";
        try (PrintWriter pw = new PrintWriter(new FileWriter(outputFilename))) {
            pw.println("Method Signature,Invocation(s),Execution Time (ms), Energy (J)");
            for (String sig : intersection) {
                ProfilerEntry p = profilerData.get(sig);
                JoularEntry j = joularData.get(sig);
                pw.printf("\"%s\",%s,%s,%.5f%n",
                        sig, p.invocations, p.timeMs, j.getAverageEnergy());
            }
        } catch (Exception e) {
            System.err.println("[EnergyMerger] Failed to write merged CSV: " + e.getMessage());
        }

        System.out.println("[EnergyMerger] Intersection output successfully saved to " + outputFilename);

        // Left Outer Join (Profiler Data Projected onto Energy)
        System.out.println("[EnergyMerger] Generating projection for all " + profilerData.size()
                + " profiler methods...");
        String projectedFilename = "../Ptidej/ptidej-Ptidej/POM/Results/projected_profiling_energy_results.csv";
        try (PrintWriter pw = new PrintWriter(new FileWriter(projectedFilename))) {
            pw.println("Method Signature,Invocation(s),Execution Time (ms),Average Energy (J)");
            for (Map.Entry<String, ProfilerEntry> entry : profilerData.entrySet()) {
                String sig = entry.getKey();
                ProfilerEntry p = entry.getValue();
                JoularEntry j = joularData.get(sig);
                String avgEnergy = (j != null) ? String.format(java.util.Locale.US, "%.5f", j.getAverageEnergy()) : "-";

                pw.printf("\"%s\",%s,%s,%s%n",
                        sig, p.invocations, p.timeMs, avgEnergy);
            }
        } catch (Exception e) {
            System.err.println("[EnergyMerger] Failed to write projected CSV: " + e.getMessage());
        }

        System.out.println("[EnergyMerger] Projection successfully saved to " + projectedFilename);
    }

    private static void processJoularFile(File file, Map<String, JoularEntry> joularData) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                // The CSV has NO header. Format is strictly: "MethodSignature",Energy
                String[] parts = line.split(",(?=([^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)");
                if (parts.length >= 2) {
                    String sig = normalizeSignature(parts[0].replace("\"", ""));

                    try {
                        // In JoularJX headerless output, energy is the second column (index 1)
                        double energy = Double.parseDouble(parts[1]);

                        JoularEntry entry = joularData.computeIfAbsent(sig, k -> new JoularEntry());
                        entry.add(energy);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[EnergyMerger] Skip reading Joular file " + file.getName() + ": " + e.getMessage());
        }
    }

    private static String normalizeSignature(String raw) {
        if (raw == null)
            return "";
        return raw.replace(", ", ",").trim();
    }

    private static class ProfilerEntry {
        String invocations;
        String timeMs;

        ProfilerEntry(String inv, String time) {
            this.invocations = inv;
            this.timeMs = time;
        }
    }

    private static class JoularEntry {
        double totalEnergy = 0;
        int fileCount = 0;

        void add(double e) {
            this.totalEnergy += e;
            this.fileCount++;
        }

        double getAverageEnergy() {
            return fileCount > 0 ? totalEnergy / fileCount : 0.0;
        }
    }
}
