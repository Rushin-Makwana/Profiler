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

        // ---------------------------------------------------------------
        // Build secondary indexes for fuzzy matching
        // ---------------------------------------------------------------
        // Index 1: class.methodName -> list of JoularEntries (overload matching)
        Map<String, List<JoularEntry>> joularByMethodName = new HashMap<>();
        // Index 2: className -> list of JoularEntries (class-level fallback)
        Map<String, List<JoularEntry>> joularByClassName = new HashMap<>();

        for (Map.Entry<String, JoularEntry> e : joularData.entrySet()) {
            String sig = e.getKey();
            JoularEntry je = e.getValue();

            // class.method key (strip params)
            String methodKey = sig.contains("(") ? sig.substring(0, sig.indexOf('(')) : sig;
            joularByMethodName.computeIfAbsent(methodKey, k -> new ArrayList<>()).add(je);

            // class key (everything before last '.')
            int lastDot = methodKey.lastIndexOf('.');
            if (lastDot > 0) {
                String classKey = methodKey.substring(0, lastDot);
                joularByClassName.computeIfAbsent(classKey, k -> new ArrayList<>()).add(je);
            }
        }

        String outputFilename = "../Ptidej/ptidej-Ptidej/POM/Results/merged_profiling_energy_results.csv";
        try (PrintWriter pw = new PrintWriter(new FileWriter(outputFilename))) {
            pw.println("Method Signature,Invocation(s),Execution Time (ms),Energy (J)");

            for (Map.Entry<String, ProfilerEntry> pe : profilerData.entrySet()) {
                String sig = pe.getKey();
                ProfilerEntry p = pe.getValue();
                double energy;

                // exact signature match
                JoularEntry exactMatch = joularData.get(sig);
                if (exactMatch != null) {
                    energy = exactMatch.getAverageEnergy();
                } else {
                    // same class.method name (overloaded method)
                    String methodKey = sig.contains("(") ? sig.substring(0, sig.indexOf('(')) : sig;
                    List<JoularEntry> overloads = joularByMethodName.get(methodKey);
                    if (overloads != null && !overloads.isEmpty()) {
                        energy = overloads.stream().mapToDouble(JoularEntry::getAverageEnergy).average().orElse(0.0);
                    } else {
                        // lass-level average
                        int lastDot = methodKey.lastIndexOf('.');
                        String classKey = lastDot > 0 ? methodKey.substring(0, lastDot) : methodKey;
                        List<JoularEntry> classEntries = joularByClassName.get(classKey);
                        if (classEntries != null && !classEntries.isEmpty()) {
                            energy = classEntries.stream().mapToDouble(JoularEntry::getAverageEnergy).average()
                                    .orElse(0.0);
                        } else {
                            energy = 0.0;
                        }
                    }
                }

                pw.printf("\"%s\",%s,%s,%.10f%n",
                        sig, p.invocations, p.timeMs, energy);
            }
        } catch (Exception e) {
            System.err.println("[EnergyMerger] Failed to write merged CSV: " + e.getMessage());
        }

    }

    private static void processJoularFile(File file, Map<String, JoularEntry> joularData) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                int lastComma = line.lastIndexOf(',');
                if (lastComma > 0 && lastComma < line.length() - 1) {
                    String rawSig = line.substring(0, lastComma).trim();
                    // Strip quotes if they were added
                    if (rawSig.startsWith("\"") && rawSig.endsWith("\"") && rawSig.length() >= 2) {
                        rawSig = rawSig.substring(1, rawSig.length() - 1);
                    }
                    String sig = normalizeSignature(rawSig);

                    try {
                        double energy = Double.parseDouble(line.substring(lastComma + 1).trim());

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
