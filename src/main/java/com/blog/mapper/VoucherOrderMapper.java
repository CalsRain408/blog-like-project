package com.blog.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.VoucherOrder;
import org.apache.ibatis.annotations.Select;

import java.util.List;


public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {
    @Select("select voucher_id from tb_voucher_order where user_id = #{userId}")
    List<Long> findByPhone(long userId);
}
