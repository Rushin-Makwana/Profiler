package com.ag.profiler;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class CallStatistics {
    
    private static final Map<String, MethodMetrics> metrics = new ConcurrentHashMap<>();

    public static void recordCall(String methodSignature, long durationNs) {
        if (methodSignature.contains("|")) {
            methodSignature = formatMethodSignature(methodSignature);
        }
        MethodMetrics m = metrics.get(methodSignature);
        if (m == null) {
            m = new MethodMetrics();
            MethodMetrics existing = metrics.putIfAbsent(methodSignature, m);
            if (existing != null) {
                m = existing;
            }
        }
        m.increment(durationNs);
    }

    private static String formatMethodSignature(String raw) {
        int idx1 = raw.indexOf('|');
        int idx2 = raw.indexOf('|', idx1 + 1);
        if (idx1 < 0 || idx2 < 0) return raw;
        
        String cls = raw.substring(0, idx1);
        String method = raw.substring(idx1 + 1, idx2);
        String desc = raw.substring(idx2 + 1);
        
        try {
            net.bytebuddy.jar.asm.Type[] argTypes = net.bytebuddy.jar.asm.Type.getArgumentTypes(desc);
            StringBuilder sb = new StringBuilder(cls).append(".").append(method).append("(");
            for (int i = 0; i < argTypes.length; i++) {
                sb.append(argTypes[i].getClassName());
                if (i < argTypes.length - 1) {
                    sb.append(",");
                }
            }
            sb.append(")");
            return sb.toString();
        } catch (Exception e) {
            return cls + "." + method + "(...)";
        }
    }

    public static void writeToCsv(String filePath) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            pw.println("Method Signature,Invocations,Total Execution Time (ms)");
            for (Map.Entry<String, MethodMetrics> entry : metrics.entrySet()) {
                double totalMs = entry.getValue().getTotalTimeNs() / 1_000_000.0;
                long count = entry.getValue().getInvocations();
                pw.println("\"" + entry.getKey() + "\"," + count + "," + String.format(java.util.Locale.US, "%.5f", totalMs));
            }
        } catch (IOException e) {
            System.err.println("[ProfilerAgent] Failed to write statistics to CSV: " + e.getMessage());
        }
    }

    public static class MethodMetrics {
        private final AtomicLong invocations = new AtomicLong(0);
        private final AtomicLong totalTimeNs = new AtomicLong(0);

        public void increment(long durationNs) {
            invocations.incrementAndGet();
            totalTimeNs.addAndGet(durationNs);
        }

        public long getInvocations() {
            return invocations.get();
        }

        public long getTotalTimeNs() {
            return totalTimeNs.get();
        }
    }
}
