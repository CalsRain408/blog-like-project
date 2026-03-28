package com.blog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.blog.dto.LoginFormDTO;
import com.blog.dto.Result;
import com.blog.entity.User;
import jakarta.servlet.http.HttpSession;


public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);
}
