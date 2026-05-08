package com.example.pavementdetection.server.service;

import com.example.pavementdetection.server.entity.User;
import com.example.pavementdetection.server.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * 注册：用户名已存在返回 false
     */
    public boolean register(String username, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            return false;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(encoder.encode(rawPassword));
        userRepository.save(user);
        return true;
    }

    /**
     * 登录：返回用户对象，失败返回 null
     */
    public User login(String username, String rawPassword) {
        return userRepository.findByUsername(username)
                .filter(u -> encoder.matches(rawPassword, u.getPassword()))
                .orElse(null);
    }
}