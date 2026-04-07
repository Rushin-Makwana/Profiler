package com.ag.profiler;

import com.ag.profiler.bytebuddy.ByteBuddy;
import com.ag.profiler.bytebuddy.asm.Advice;
import com.ag.profiler.bytebuddy.matcher.ElementMatchers;

public class TestAdviceFormat {
    public static class Target {
        public void myMethod(String x, int y) throws Exception {}
    }

    public static class MyAdvice {
        @Advice.OnMethodEnter
        public static void enter(
                @Advice.Origin String def,
                @Advice.Origin("#t.#m#s") String t1
        ) {
            System.out.println("def: " + def);
            System.out.println("t1: " + t1);
        }
    }

    public static void main(String[] args) throws Exception {
        Class<?> clazz = new ByteBuddy()
                .subclass(Target.class)
                .method(ElementMatchers.named("myMethod"))
                .intercept(Advice.to(MyAdvice.class))
                .make()
                .load(TestAdviceFormat.class.getClassLoader())
                .getLoaded();
        
        Object instance = clazz.getDeclaredConstructor().newInstance();
        clazz.getMethod("myMethod", String.class, int.class).invoke(instance, "test", 42);
    }
}
