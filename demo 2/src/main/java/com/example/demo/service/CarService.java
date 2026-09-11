package com.example.demo.service;

import com.example.demo.dto.CarRequest;
import com.example.demo.dto.CarResponse;
import com.example.demo.entity.Car;
import com.example.demo.entity.CarStatus;
import com.example.demo.repository.CarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.dto.CarReportResponse;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class CarService {

    private final CarRepository carRepository;

    public CarService(CarRepository carRepository) {
        this.carRepository = carRepository;
    }

    public CarResponse create(CarRequest request) {

        Car car = new Car();

        car.setBrand(request.getBrand());
        car.setModel(request.getModel());
        car.setYear(request.getYear());
        car.setVin(request.getVin());
        car.setPrice(request.getPrice());

        car.setStatus(CarStatus.IN_STOCK);
        car.setCreatedAt(LocalDateTime.now());

        Car savedCar = carRepository.save(car);

        return toResponse(savedCar);
    }

    public List<CarResponse> findAll() {
        return carRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public CarResponse findById(Long id) {

        Car car = carRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Автомобиль с id=" + id + " не найден")
                );

        return toResponse(car);
    }

    private CarResponse toResponse(Car car) {

        CarResponse response = new CarResponse();

        response.setId(car.getId());
        response.setBrand(car.getBrand());
        response.setModel(car.getModel());
        response.setYear(car.getYear());
        response.setVin(car.getVin());
        response.setPrice(car.getPrice());
        response.setStatus(car.getStatus());
        response.setCreatedAt(car.getCreatedAt());
        response.setSoldAt(car.getSoldAt());

        return response;
    }

    public CarResponse update(Long id, CarRequest request) {

      Car car = carRepository.findById(id)
              .orElseThrow(() ->
                      new RuntimeException("Автомобиль с id=" + id + " не найден")
              );
  
      car.setBrand(request.getBrand());
      car.setModel(request.getModel());
      car.setYear(request.getYear());
      car.setVin(request.getVin());
      car.setPrice(request.getPrice());
  
      return toResponse(carRepository.save(car));
  }

  public CarResponse sell(Long id) {

    Car car = carRepository.findById(id)
            .orElseThrow(() ->
                    new RuntimeException("Автомобиль с id=" + id + " не найден")
            );

    if (car.getStatus() == CarStatus.SOLD) {
        throw new RuntimeException("Автомобиль уже продан");
    }

    car.setStatus(CarStatus.SOLD);
    car.setSoldAt(LocalDateTime.now());

    return toResponse(carRepository.save(car));
  }

  public CarReportResponse getReport() {

    long inStock = carRepository.countByStatus(CarStatus.IN_STOCK);
    long sold = carRepository.countByStatus(CarStatus.SOLD);

    return new CarReportResponse(
            inStock,
            sold,
            inStock + sold
    );
  }

    public void delete(Long id) {

      if (!carRepository.existsById(id)) {
          throw new RuntimeException(
                  "Автомобиль с id=" + id + " не найден"
          );
      }

      carRepository.deleteById(id);
  }
}