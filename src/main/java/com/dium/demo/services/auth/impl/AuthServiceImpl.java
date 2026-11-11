package com.dium.demo.services.auth.impl;

import com.dium.demo.dto.responses.AuthResponse;
import com.dium.demo.dto.requests.LoginRequest;
import com.dium.demo.dto.requests.RegisterRequest;
import com.dium.demo.dto.responses.UserResponse;
import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.AccessDeniedException;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.mappers.UserMapper;
import com.dium.demo.models.User;
import com.dium.demo.repositories.UserRepository;
import com.dium.demo.services.auth.AuthService;
import com.dium.demo.services.auth.CustomUserDetailsService;
import com.dium.demo.services.auth.JwtService;
import com.dium.demo.services.auth.OtpCodeSenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final OtpCodeSenderService otpCodeSenderService;

    @Transactional
    @Override
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
        } catch (LockedException ex) {
            otpCodeSenderService.sendCode(request.phone());
            throw new BusinessLogicException("ACCOUNT_LOCKED:Please confirm your phone number. Code sent.");
        } catch (BadCredentialsException ex) {
            throw new BadCredentialsException("Phone or password is incorrect!");
        } catch (Exception e) {
            throw new RuntimeException("An error was occur while authenticating user: " + e.getMessage());
        }
    }

    @Transactional
    @Override
    public UserResponse register(RegisterRequest request) {
        checkHasRegistered(request.phone(), "User has already registered!");
        User user = userMapper.toEntity(request);

        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.CLIENT);
        user.setIsConfirmed(false);

        User saved = userRepository.save(user);

        otpCodeSenderService.sendCode(saved.getPhone());

        return userMapper.toResponse(saved);
    }

    // TODO wanna change this method to the setting existing user to venue owner
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @Override
    public UserResponse venueRegister(RegisterRequest request) {
        checkHasRegistered(request.phone(), "Venue owner has already registered!");

        User user = userMapper.toEntity(request);

        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.VENUE_OWNER);

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    @Override
    public UserResponse getMe() {
        User user = userDetailsService.getCurrentUser();

        if(!user.isAccountNonLocked())
            throw new AccessDeniedException("Please, logout and confirm phone!");

        return userMapper.toResponse(user);
    }

    private void checkHasRegistered(String phone, String message) {
        if(userRepository.existsByPhone(phone))
            throw new AccessDeniedException(message);
    }
}
