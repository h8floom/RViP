package com.example.sorting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ParallelMergeSort {

    private ParallelMergeSort() {}

    public record Result<T>(List<T> items, int usedThreads) {}

    public static <T> Result<T> sort(List<T> input, Comparator<? super T> comparator, int threads)
            throws InterruptedException {
        Objects.requireNonNull(comparator, "comparator");
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be positive");
        }
        List<T> values = new ArrayList<>(input);
        if (values.isEmpty()) {
            return new Result<>(List.of(), 0);
        }

        int workers = Math.min(threads, values.size());
        List<T> buffer = new ArrayList<>(values);
        List<Range> runs = new ArrayList<>(workers);
        List<Runnable> tasks = new ArrayList<>(workers);

        for (int i = 0; i < workers; i++) {
            int from = (int) ((long) values.size() * i / workers);
            int to = (int) ((long) values.size() * (i + 1) / workers);
            runs.add(new Range(from, to));
            tasks.add(() -> sortPartition(values, buffer, from, to, comparator));
        }

        try (ExecutorService pool = Executors.newFixedThreadPool(workers)) {
            runRound(pool, tasks);
            while (runs.size() > 1) {
                List<Range> next = new ArrayList<>((runs.size() + 1) / 2);
                tasks.clear();
                for (int i = 0; i + 1 < runs.size(); i += 2) {
                    Range left = runs.get(i);
                    Range right = runs.get(i + 1);
                    next.add(new Range(left.from(), right.to()));
                    tasks.add(() -> merge(values, buffer, left.from(), left.to(), right.to(), comparator));
                }
                if (runs.size() % 2 != 0) {
                    next.add(runs.getLast());
                }
                runRound(pool, tasks);
                runs = next;
            }
        }
        return new Result<>(Collections.unmodifiableList(values), workers);
    }

    private static <T> void sortPartition(List<T> values, List<T> buffer, int from, int to,
                                          Comparator<? super T> comparator) {
        if (to - from < 2) {
            return;
        }
        int middle = from + (to - from) / 2;
        sortPartition(values, buffer, from, middle, comparator);
        sortPartition(values, buffer, middle, to, comparator);
        merge(values, buffer, from, middle, to, comparator);
    }

    private static <T> void merge(List<T> values, List<T> buffer, int from, int middle, int to,
                                  Comparator<? super T> comparator) {
        for (int i = from; i < to; i++) {
            buffer.set(i, values.get(i));
        }
        int left = from;
        int right = middle;
        for (int i = from; i < to; i++) {
            if (right == to || (left < middle && comparator.compare(buffer.get(left), buffer.get(right)) <= 0)) {
                values.set(i, buffer.get(left++));
            } else {
                values.set(i, buffer.get(right++));
            }
        }
    }

    private static void runRound(ExecutorService pool, List<Runnable> tasks) throws InterruptedException {
        var completed = pool.invokeAll(tasks.stream().map(Executors::callable).toList());
        for (var future : completed) {
            try {
                future.get();
            } catch (ExecutionException exception) {
                throw new IllegalStateException("Merge sort worker failed", exception.getCause());
            }
        }
    }

    private record Range(int from, int to) {}
}
