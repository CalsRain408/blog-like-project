package com.blog.service.impl;

import com.blog.entity.Reservation;
import com.blog.mapper.ReservationMapper;
import com.blog.service.IReservationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReservationServiceImpl implements IReservationService {
    @Autowired
    private ReservationMapper reservationMapper;

    @Override
    public void insert(Reservation reservation) {
        reservationMapper.insert(reservation);
    }

    @Override
    public List<Reservation> findByPhone(String phone) {
        return reservationMapper.findByPhone(phone);
    }

}
