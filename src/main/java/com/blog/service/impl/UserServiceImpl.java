package com.blog.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.blog.dto.LoginFormDTO;
import com.blog.dto.Result;
import com.blog.dto.UserDTO;
import com.blog.entity.User;
import com.blog.mapper.UserMapper;
import com.blog.service.IUserService;
import com.blog.utils.RedisConstants;
import com.blog.utils.RegexUtils;
import com.blog.utils.SystemConstants;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 将手机验证码存储至Redis中，适用于分布式集群session无法共享的情况
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 判断手机号是否合法
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式不正确");
        }

        // 2. 手机号合法，生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 3. 保存验证码到session
//        session.setAttribute(SystemConstants.VERIFY_CODE, code);
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code,
                RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 4. 发送验证码
        log.info("验证码: {}", code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();

        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式不正确");
        }

        String redisCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);

        if (code == null || !code.equals(redisCode)){
            return Result.fail("验证码不正确");
        }

        // 判断用户是否存在
        // 使用MyBatis-Plus框架，用于根据指定条件从数据库中查询用户记录
        User user = this.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone));

        if (user == null){
            // 用户不存在
            user = createUserWithPhone(phone);
        }

        // 保存用户信息至redis，便于后面逻辑的判断（比如登录判断、随时取用户信息，减少对数据库的查询）
//        session.setAttribute(SystemConstants.LOGIN_USER, user);

        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);

        // 将对象中的字段全部转为String类型
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor(
                        (fieldName, fieldValue) -> fieldValue.toString()
                ));

        // Redis key
        String token = UUID.randomUUID().toString();
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);  // 设置键的过期时间

        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 数据库中保存记录
        this.save(user);
        return user;
    }
}
