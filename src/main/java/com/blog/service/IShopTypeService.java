package com.blog.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.blog.dto.Result;
import com.blog.entity.ShopType;


public interface IShopTypeService extends IService<ShopType> {

    Result queryTypeList();
}
