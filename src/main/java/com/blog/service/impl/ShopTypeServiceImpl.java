package com.blog.service.impl;


import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.dto.Result;
import com.blog.entity.Shop;
import com.blog.entity.ShopType;
import com.blog.mapper.ShopTypeMapper;
import com.blog.service.IShopTypeService;
import com.blog.utils.RedisConstants;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY + UUID.randomUUID();
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        List<ShopType> typeList = null;

        if (shopJson != null && shopJson.isBlank()){
            typeList = JSONUtil.toList(shopJson, ShopType.class);

            return Result.ok(typeList);
        }

        typeList = this.list(new LambdaQueryWrapper<ShopType>()
                .orderByAsc(ShopType::getSort));

        if (typeList == null || typeList.isEmpty()){
            return Result.fail("店铺类型不存在");
        }

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList),
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return Result.ok(typeList);
    }
}
