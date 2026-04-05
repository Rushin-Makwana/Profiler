package com.ag.profiler;

import java.util.ArrayList;
import java.util.List;

public class SampleApplication {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Sample Application Started...");
        
        // Some our own method call
        doWork();
        
        // Some JDK method call
        List<String> list = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            list.add("Item " + i);
        }
        
        Thread.sleep(100);
        
        System.out.println("Sample Application Finished.");
    }
    
    private static void doWork() throws InterruptedException {
        System.out.println("Doing work...");
        Thread.sleep(200);
    }
}
