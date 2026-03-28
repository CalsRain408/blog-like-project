package com.blog.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shop:type:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";

    // 二级缓存：优惠券详情
    public static final String CACHE_VOUCHER_KEY = "cache:voucher:";
    public static final Long CACHE_VOUCHER_TTL = 30L;           // Redis TTL：30分钟
    // 二级缓存：店铺维度的优惠券列表
    public static final String CACHE_VOUCHER_SHOP_KEY = "cache:voucher:shop:";
    public static final Long CACHE_VOUCHER_SHOP_TTL = 10L;      // Redis TTL：10分钟
    // Redis Pub/Sub 频道：用于通知各实例失效本地Caffeine缓存
    public static final String CACHE_VOUCHER_INVALIDATION_CHANNEL = "cache:voucher:invalidation";
}
