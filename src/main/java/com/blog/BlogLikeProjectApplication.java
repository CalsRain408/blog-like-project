package com.blog;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.blog.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)  // 暴露动态代理对象
@EnableScheduling
public class BlogLikeProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogLikeProjectApplication.class, args);
    }

}
