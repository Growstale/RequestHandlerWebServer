package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.feature.entity.Role;
import com.vodchyts.backend.feature.entity.User;
import com.vodchyts.backend.feature.repository.RoleRepository;
import com.vodchyts.backend.feature.repository.UserRepository;
import com.vodchyts.backend.security.JwtUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder, JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    public String register(@RequestParam String login,
                           @RequestParam String password,
                           @RequestParam String roleName) {

        if (userRepository.existsByLogin(login)) {
            return "User already exists";
        }

        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        User user = new User();
        user.setLogin(login);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);

        userRepository.save(user);
        return "User registered";
    }

    public String login(@RequestParam String login, @RequestParam String password) {
        User user = userRepository.findByLogin(login)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        return jwtUtils.generateToken(user.getLogin(), user.getRole().getRoleName());
    }
}
