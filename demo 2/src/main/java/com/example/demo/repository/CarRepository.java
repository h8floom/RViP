package com.example.demo.repository;

import com.example.demo.entity.Car;
import com.example.demo.entity.CarStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarRepository extends JpaRepository<Car, Long> {

    long countByStatus(CarStatus status);
}