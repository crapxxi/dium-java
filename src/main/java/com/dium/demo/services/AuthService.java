package com.dium.demo.services;

import com.dium.demo.dto.auth.AuthResponse;
import com.dium.demo.dto.auth.LoginRequest;
import com.dium.demo.dto.auth.RegisterRequest;
import com.dium.demo.dto.auth.UserResponse;
import com.dium.demo.enums.UserRole;
import com.dium.demo.mappers.UserMapper;
import com.dium.demo.models.User;
import com.dium.demo.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.phone(), request.password())
            );

            UserDetails userDetails = userDetailsService.loadUserByUsername(request.phone());
            String jwtToken = jwtService.generateToken(userDetails);
            return new AuthResponse(
                    jwtToken,
                    request.phone()
            );
        } catch (Exception e) {
            throw new RuntimeException("Phone or password is incorrect!");
        }
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if(userRepository.existsByPhone(request.phone()))
            throw new RuntimeException("Phone is already in the system");

        User user = userMapper.toEntity(request);

        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.CLIENT);

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    @Transactional
    public UserResponse venueRegister(RegisterRequest request) {
        if(userRepository.existsByPhone(request.phone()))
            throw new RuntimeException("Phone is already in the system");

        User user = userMapper.toEntity(request);

        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.VENUE_OWNER);

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(UserDetails userDetails) {
        if (userDetails instanceof User user) {
            return userMapper.toResponse(user);
        }
        throw new RuntimeException("Not Authorized");
    }
}
