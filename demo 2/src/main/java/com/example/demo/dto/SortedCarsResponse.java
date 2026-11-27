package com.example.demo.dto;

import java.util.List;

public record SortedCarsResponse(
        List<CarResponse> cars,
        int requestedThreads,
        int usedThreads,
        long sortingTimeNanos
) {}
