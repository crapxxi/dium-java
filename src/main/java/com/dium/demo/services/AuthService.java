package com.dium.demo.services;

import com.dium.demo.dto.responses.AuthResponse;
import com.dium.demo.dto.requests.LoginRequest;
import com.dium.demo.dto.requests.RegisterRequest;
import com.dium.demo.dto.responses.UserResponse;
import com.dium.demo.enums.UserRole;
import com.dium.demo.mappers.UserMapper;
import com.dium.demo.models.User;
import com.dium.demo.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
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
        } catch (AuthenticationCredentialsNotFoundException ex) {
            throw new BadCredentialsException("Phone or password is incorrect!");
        } catch (Exception e) {
            throw new RuntimeException("An error was occur while authenticating user: " + e.getMessage());
        }
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        checkHasRegistered(request.phone(), "User has already registered!");

        User user = userMapper.toEntity(request);

        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.CLIENT);

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse venueRegister(RegisterRequest request) {
        checkHasRegistered(request.phone(), "Venue owner has already registered!");

        User user = userMapper.toEntity(request);

        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.VENUE_OWNER);

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getMe() {
        User user = userDetailsService.getCurrentUser();

        return userMapper.toResponse(user);
    }

    private void checkHasRegistered(String phone, String message) {
        if(userRepository.existsByPhone(phone))
            throw new AccessDeniedException(message);
    }
}
