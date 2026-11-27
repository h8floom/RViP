package com.example.sorting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public final class ParallelMergeSortBenchmark {

    private static final int SIZE = 500_000;
    private static final int REPEATS = 5;
    private static final int[] THREAD_COUNTS = {1, 2, 4, 5, 8};

    private ParallelMergeSortBenchmark() {}

    public static void main(String[] args) throws InterruptedException {
        verifySmallCases();

        List<Integer> input = new Random(42).ints(SIZE, 0, SIZE / 4).boxed().toList();
        List<Integer> expected = new ArrayList<>(input);
        expected.sort(Comparator.naturalOrder());
        long[][] timings = new long[THREAD_COUNTS.length][REPEATS];

        for (int threads : THREAD_COUNTS) {
            require(ParallelMergeSort.sort(input, Comparator.naturalOrder(), threads).items().equals(expected),
                    "Warm-up produced an incorrect result with " + threads + " threads");
        }
        for (int repeat = 0; repeat < REPEATS; repeat++) {
            for (int step = 0; step < THREAD_COUNTS.length; step++) {
                int index = (step + repeat) % THREAD_COUNTS.length;
                int threads = THREAD_COUNTS[index];
                long start = System.nanoTime();
                var result = ParallelMergeSort.sort(input, Comparator.naturalOrder(), threads);
                timings[index][repeat] = System.nanoTime() - start;
                require(result.items().equals(expected) && result.usedThreads() == threads,
                        "Unexpected result with " + threads + " threads");
            }
        }

        var workerIds = ConcurrentHashMap.<Long>newKeySet();
        ParallelMergeSort.sort(input.subList(0, 1000), (left, right) -> {
            workerIds.add(Thread.currentThread().threadId());
            return Integer.compare(left, right);
        }, 5);
        require(workerIds.size() == 5, "Expected comparisons on all five worker threads");

        System.out.printf("Verified: example, empty input, stability, worker limit, five actual workers%n");
        System.out.printf("Size: %,d, seed: 42, repeats: %d%n", SIZE, REPEATS);
        System.out.printf("%8s %12s %12s%n", "Threads", "Median (ms)", "Speedup");
        double baseline = median(timings[0]);
        for (int index = 0; index < THREAD_COUNTS.length; index++) {
            double median = median(timings[index]);
            System.out.printf(Locale.ROOT, "%8d %12.3f %11.2fx%n",
                    THREAD_COUNTS[index], median / 1_000_000, baseline / median);
        }
    }

    private static void verifySmallCases() throws InterruptedException {
        List<Integer> original = List.of(3, 2, 6, 9, 2, 3, 5, 8, 2, 1, 11);
        var input = new ArrayList<>(original);
        List<Integer> expected = List.of(1, 2, 2, 2, 3, 3, 5, 6, 8, 9, 11);
        for (int threads : new int[]{1, 2, 5, 20}) {
            var result = ParallelMergeSort.sort(input, Comparator.naturalOrder(), threads);
            require(result.items().equals(expected) && result.usedThreads() == Math.min(threads, input.size()),
                    "Example failed with " + threads + " threads");
        }
        require(input.equals(original), "The input list changed");
        var empty = ParallelMergeSort.sort(List.<Integer>of(), Comparator.naturalOrder(), 5);
        require(empty.items().isEmpty() && empty.usedThreads() == 0, "Empty input failed");

        record Entry(int key, String value) {}
        var first = new Entry(1, "first");
        var second = new Entry(1, "second");
        var third = new Entry(1, "third");
        var larger = new Entry(2, "larger");
        var stable = ParallelMergeSort.sort(List.of(first, larger, second, third),
                Comparator.comparingInt(Entry::key), 3);
        require(stable.items().equals(List.of(first, second, third, larger)), "Ties are not stable");
    }

    private static long median(long[] timings) {
        long[] ordered = timings.clone();
        Arrays.sort(ordered);
        return ordered[ordered.length / 2];
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
