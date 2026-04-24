package com.dium.demo.services;

import com.dium.demo.dto.requests.ResetPasswordRequest;
import com.dium.demo.dto.responses.AuthResponse;
import com.dium.demo.dto.requests.LoginRequest;
import com.dium.demo.dto.requests.RegisterRequest;
import com.dium.demo.dto.responses.UserResponse;
import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.AccessDeniedException;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.mappers.UserMapper;
import com.dium.demo.models.OtpCode;
import com.dium.demo.models.User;
import com.dium.demo.repositories.OtpRepository;
import com.dium.demo.repositories.UserRepository;
import com.dium.demo.whatsapp.WhatsAppService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final OtpRepository otpRepository;
    private final WhatsAppService whatsAppService;
    private final SecureRandom secureRandom = new SecureRandom();

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
        } catch (LockedException ex) {
            sendRegistrationCode(request.phone());
            throw new BusinessLogicException("ACCOUNT_LOCKED:Please confirm your phone number. Code sent.");
        } catch (BadCredentialsException ex) {
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
        user.setIsConfirmed(false);

        User saved = userRepository.save(user);

        sendRegistrationCode(saved.getPhone());

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

        if(!user.isAccountNonLocked())
            throw new AccessDeniedException("Please, logout and confirm phone!");

        return userMapper.toResponse(user);
    }
    @Transactional
    public AuthResponse activateAccount(String phone, String code) {
        OtpCode cachedOtp = otpRepository.findById(phone)
                .orElseThrow(() -> new BusinessLogicException("Код истек или не найден"));

        if (!cachedOtp.getCode().equals(code)) {
            throw new BusinessLogicException("Неверный код активации");
        }

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new EntityNotFoundException("Пользователь не найден"));

        user.setIsConfirmed(true);
        userRepository.save(user);

        otpRepository.delete(cachedOtp);

        return new AuthResponse(jwtService.generateToken(user), phone);
    }

    private void checkHasRegistered(String phone, String message) {
        if(userRepository.existsByPhone(phone))
            throw new AccessDeniedException(message);
    }

    private void sendRegistrationCode(String phone) {
//        if(userRepository.existsByPhone(phone)) {
//            throw new BusinessLogicException("User with this phone exists in db");
//        }
        String code = String.format("%04d", secureRandom.nextInt(10000));

        OtpCode otpCode = OtpCode.builder()
                .phone(phone)
                .code(code)
                .build();
        otpRepository.save(otpCode);
        whatsAppService.sendMessage(phone, String.format("Код подтверждения DIUM: *%s*\n⚠\uFE0F Если вы не пытались войти в систему, просто проигнорируйте это сообщение. Код истекает через 5 минут.", code));
    }

    @Transactional
    public void resendActivationCode(String phone) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (user.getIsConfirmed()) {
            throw new BusinessLogicException("Account is already confirmed");
        }

        sendRegistrationCode(phone);
    }

    public void requestPasswordReset(String phone) {
        userRepository.findByPhone(phone)
                .orElseThrow(() -> new BusinessLogicException("Пользователь с таким номером не найден"));

        String code = String.format("%04d", secureRandom.nextInt(10000));

        otpRepository.save(new OtpCode(phone, code));

        String message = String.format("Код для сброса пароля DIUM: *%s*\n⚠\uFE0F Если вы не пытались войти в систему, просто проигнорируйте это сообщение. Код истекает через 5 минут.", code);
        whatsAppService.sendMessage(phone, message);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        OtpCode otpCode = otpRepository.findById(request.phone())
                .orElseThrow(() -> new BusinessLogicException("Код истек или не запрашивался. Попробуйте еще раз."));

        if (!otpCode.getCode().equals(request.code())) {
            throw new BusinessLogicException("Неверный код подтверждения");
        }

        User user = userRepository.findByPhone(request.phone())
                .orElseThrow(() -> new BusinessLogicException("Пользователь не найден"));

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        otpRepository.delete(otpCode);
    }
}
