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
            File[] files = dir
                    .listFiles((d, name) -> name.startsWith("joularJX-") && name.endsWith("-instrumented-methods.csv"));
            if (files != null) {
                for (File jFile : files) {
                    processJoularFile(jFile, joularData);
                }
            }
        }

        if (joularData.isEmpty()) {
            System.out.println("[EnergyMerger] No JoularJX energy data found in " + joularDir + " to merge.");
            return;
        }

        // Inner join both
        Set<String> intersection = new HashSet<>(profilerData.keySet());
        intersection.retainAll(joularData.keySet());

        System.out.println("[EnergyMerger] Found " + intersection.size() + " correlated methods. Writing output...");

        String outputFilename = "merged_profiling_energy_results.csv";
        try (PrintWriter pw = new PrintWriter(new FileWriter(outputFilename))) {
            pw.println("Method Signature,Invocation(s),Execution Time (ms), Energy (J)");
            for (String sig : intersection) {
                ProfilerEntry p = profilerData.get(sig);
                JoularEntry j = joularData.get(sig);
                pw.printf("\"%s\",%s,%s,%.5f%n",
                        sig, p.invocations, p.timeMs, j.energy);
            }
        } catch (Exception e) {
            System.err.println("[EnergyMerger] Failed to write merged CSV: " + e.getMessage());
        }

        System.out.println("[EnergyMerger] Output successfully saved to " + outputFilename);
    }

    private static void processJoularFile(File file, Map<String, JoularEntry> joularData) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String header = br.readLine();
            if (header == null)
                return;

            // Allow variations like EnergyJ vs Energy(J)
            String[] cols = header.split(",");
            int energyIdx = -1;
            for (int i = 0; i < cols.length; i++) {
                String col = cols[i].trim();
                if (col.equals("Energy(J)") || col.equals("EnergyJ"))
                    energyIdx = i;
            }

            if (energyIdx == -1)
                return; // malformed?

            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",(?=([^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)");
                if (parts.length > energyIdx) {
                    String sig = normalizeSignature(parts[0].replace("\"", ""));

                    try {
                        double energy = Double.parseDouble(parts[energyIdx]);

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
        double energy = 0;

        void add(double e) {
            this.energy += e;
        }
    }
}
