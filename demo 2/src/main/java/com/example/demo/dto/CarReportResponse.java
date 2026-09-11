package com.example.demo.dto;

public class CarReportResponse {

    private long inStock;
    private long sold;
    private long total;

    public CarReportResponse(long inStock, long sold, long total) {
        this.inStock = inStock;
        this.sold = sold;
        this.total = total;
    }

    public long getInStock() {
        return inStock;
    }

    public long getSold() {
        return sold;
    }

    public long getTotal() {
        return total;
    }
}