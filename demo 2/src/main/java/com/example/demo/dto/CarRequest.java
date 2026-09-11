package com.example.demo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CarRequest {

    @NotBlank(message = "Марка автомобиля обязательна")
    @Size(max = 100, message = "Марка не должна превышать 100 символов")
    private String brand;

    @NotBlank(message = "Модель автомобиля обязательна")
    @Size(max = 100, message = "Модель не должна превышать 100 символов")
    private String model;

    @NotNull(message = "Год выпуска обязателен")
    @Min(value = 1886, message = "Некорректный год выпуска")
    @Max(value = 2100, message = "Некорректный год выпуска")
    private Integer year;

    @NotBlank(message = "VIN обязателен")
    @Size(min = 17, max = 17, message = "VIN должен содержать 17 символов")
    private String vin;

    @NotNull(message = "Цена обязательна")
    @DecimalMin(value = "0.01", message = "Цена должна быть больше 0")
    private BigDecimal price;

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getVin() {
        return vin;
    }

    public void setVin(String vin) {
        this.vin = vin;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}