package com.example.vacancyparser.benchmark;

import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.TimeUnit;

public class BenchmarkConfig {

    public static ChainedOptionsBuilder createBaseOptions() {
        return new OptionsBuilder()
                .warmupIterations(2)
                .warmupTime(TimeValue.seconds(2))
                .measurementIterations(3)
                .measurementTime(TimeValue.seconds(3))
                .forks(1)
                .timeUnit(TimeUnit.MICROSECONDS)
                .shouldDoGC(true)
                .shouldFailOnError(true)
                .jvmArgs("-Xmx2g", "-Xms2g");
    }

    public static Options createAverageTimeOptions(Class<?> benchmarkClass) {
        return createBaseOptions()
                .include(benchmarkClass.getSimpleName())
                .mode(org.openjdk.jmh.annotations.Mode.AverageTime)
                .build();
    }

    public static Options createThroughputOptions(Class<?> benchmarkClass) {
        return createBaseOptions()
                .include(benchmarkClass.getSimpleName())
                .mode(org.openjdk.jmh.annotations.Mode.Throughput)
                .timeUnit(TimeUnit.SECONDS)
                .build();
    }

    public static Options createAllOptions(Class<?> benchmarkClass) {
        return createBaseOptions()
                .include(benchmarkClass.getSimpleName())
                .mode(org.openjdk.jmh.annotations.Mode.All)
                .build();
    }
}