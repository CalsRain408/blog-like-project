package com.blog.mapper;


import com.blog.entity.Reservation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReservationMapper {

    //1.添加预约信息
    @Insert("insert into reservation(name,phone,communication_time) values(#{name},#{phone},#{communicationTime})")
    void insert(Reservation reservation);
    //2.根据手机号查询预约信息
    @Select("select * from reservation where phone=#{phone}")
    List<Reservation> findByPhone(String phone);

}
