package com.blog.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.blog.dto.Result;
import com.blog.entity.ShopType;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopTypeService extends IService<ShopType> {

    Result queryTypeList();
}
