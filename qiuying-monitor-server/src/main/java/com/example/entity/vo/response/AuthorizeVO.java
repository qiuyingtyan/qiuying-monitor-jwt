package com.example.entity.vo.response;

import lombok.Data;

import java.util.Date;

@Data
public class AuthorizeVO {
    // 用户名
    String username;
    // 角色
    String role;
    // 令牌
    String token;
    // 过期时间
    Date expire;
}