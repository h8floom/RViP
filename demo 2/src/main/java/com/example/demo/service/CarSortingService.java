package com.example.demo.service;

import com.example.demo.dto.CarResponse;
import com.example.demo.dto.SortedCarsResponse;
import com.example.sorting.ParallelMergeSort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;

@Service
public class CarSortingService {

    public static final int MAX_THREADS = 32;
    private static final Comparator<CarResponse> BY_ID = Comparator.comparing(CarResponse::getId);

    private final CarService carService;
    private final Semaphore sortSlots = new Semaphore(2);

    public CarSortingService(CarService carService) {
        this.carService = carService;
    }

    public SortedCarsResponse sortedCars(int threads) {
        if (threads < 1 || threads > MAX_THREADS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "threads must be between 1 and " + MAX_THREADS);
        }
        if (!sortSlots.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Sorting workers are busy");
        }
        try {
            List<CarResponse> cars = carService.findAll();
            long start = System.nanoTime();
            var result = ParallelMergeSort.sort(cars, BY_ID, threads);
            long sortingTime = System.nanoTime() - start;
            return new SortedCarsResponse(result.items(), threads, result.usedThreads(), sortingTime);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Sorting was interrupted", exception);
        } finally {
            sortSlots.release();
        }
    }
}
