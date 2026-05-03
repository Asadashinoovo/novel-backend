package com.djs.novel.filter;


import com.djs.novel.dto.UserDTO;
import com.djs.novel.entity.User;
import com.djs.novel.util.UserHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthenticationFilter implements HandlerInterceptor {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request,
                            HttpServletResponse response,
                            Object handler) throws Exception {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":401,\"errorMsg\":\"未登录\"}");
            return false;
        }

        String token = authHeader.substring(7);

        try {
            //根据当前的token从redis里面拿元素
            String userJson=stringRedisTemplate.opsForValue().get("token:"+token);

            if (userJson == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"errorMsg\":\"登录已过期\"}");
                return false;
            }

            User user = objectMapper.readValue(userJson, User.class);
            UserDTO userDTO = new UserDTO();
            userDTO.setUsername(user.getUsername());
            userDTO.setPassword(user.getPassword());
            userDTO.setImgUrl(user.getImgUrl());
            userDTO.setId(user.getId());
            UserHolder.saveUser(userDTO);

            return true;

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":401,\"errorMsg\":\"无效Token\"}");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        UserHolder.removeUser();
    }
}
