package com.blog.service;

import com.blog.entity.Reservation;

import java.util.List;

public interface IReservationService {
    void insert(Reservation reservation);

    List<Reservation> findByPhone(String phone);
}
