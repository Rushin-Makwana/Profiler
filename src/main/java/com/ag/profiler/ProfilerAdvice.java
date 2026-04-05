package com.ag.profiler;

import net.bytebuddy.asm.Advice;

public class ProfilerAdvice {

    // ThreadLocal to prevent recursive tracking
    public static final ThreadLocal<Boolean> IN_PROFILER = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.Origin("#t|#m|#d") String methodSignature, 
                               @Advice.Local("startTime") long startTime) {
        startTime = System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(@Advice.Origin("#t|#m|#d") String methodSignature, 
                              @Advice.Local("startTime") long startTime) {
        if (IN_PROFILER.get()) {
            return;
        }
        long duration = System.nanoTime() - startTime;
        IN_PROFILER.set(true);
        try {
            CallStatistics.recordCall(methodSignature, duration);
        } finally {
            IN_PROFILER.set(false);
        }
    }
}
