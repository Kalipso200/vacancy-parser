package com.example.vacancyparser.benchmark;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.TimeUnit;

public class BenchmarkRunner {
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(ParsingBenchmark.class.getSimpleName())
                .include(VacancyProcessingBenchmark.class.getSimpleName())
                .warmupIterations(2)
                .warmupTime(TimeValue.seconds(2))
                .measurementIterations(3)
                .measurementTime(TimeValue.seconds(3))
                .forks(1)
                .timeUnit(TimeUnit.MICROSECONDS)
                .shouldDoGC(true)
                .shouldFailOnError(true)
                .jvmArgs("-Xmx2g", "-Xms2g")
                .build();

        new Runner(opt).run();
    }
}