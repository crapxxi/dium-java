package com.dium.demo.services;

import com.dium.demo.dto.requests.LoginRequest;
import com.dium.demo.dto.requests.RegisterRequest;
import com.dium.demo.dto.responses.AuthResponse;
import com.dium.demo.dto.responses.UserResponse;
import com.dium.demo.enums.UserRole;
import com.dium.demo.mappers.UserMapper;
import com.dium.demo.models.User;
import com.dium.demo.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private CustomUserDetailsService userDetailsService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private final String TEST_PHONE = "+79991234567";
    private final String TEST_PASSWORD = "password123";
    private final String ENCODED_PASSWORD = "encodedPassword123";

    private User dummyUser;
    private UserResponse dummyUserResponse;

    @BeforeEach
    void setUp() {
        dummyUser = new User();
        dummyUser.setId(1L);
        dummyUser.setPhone(TEST_PHONE);

        dummyUserResponse = new UserResponse(1L, TEST_PHONE, "Test Name", UserRole.CLIENT);
    }

    @Test
    void login_Success_ReturnsAuthResponse() {
        LoginRequest request = new LoginRequest(TEST_PHONE, TEST_PASSWORD);
        UserDetails mockUserDetails = mock(UserDetails.class);
        String expectedToken = "jwt-token-string";

        when(userDetailsService.loadUserByUsername(TEST_PHONE)).thenReturn(mockUserDetails);
        when(jwtService.generateToken(mockUserDetails)).thenReturn(expectedToken);

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo(expectedToken);
        assertThat(response.phone()).isEqualTo(TEST_PHONE);

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(userDetailsService).loadUserByUsername(TEST_PHONE);
        verify(jwtService).generateToken(mockUserDetails);
    }

    @Test
    void login_InvalidCredentials_ThrowsBadCredentialsException() {
        LoginRequest request = new LoginRequest(TEST_PHONE, TEST_PASSWORD);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new AuthenticationCredentialsNotFoundException("Not found"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Phone or password is incorrect!");
    }

    @Test
    void login_GenericException_ThrowsRuntimeException() {
        LoginRequest request = new LoginRequest(TEST_PHONE, TEST_PASSWORD);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new RuntimeException("Database down"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("An error was occur while authenticating user: Database down");
    }


    @Test
    void register_Success_SavesUserAndReturnsResponse() {
        RegisterRequest request = new RegisterRequest(TEST_PHONE, "Test Name", TEST_PASSWORD);

        when(userRepository.existsByPhone(TEST_PHONE)).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(dummyUser);
        when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(User.class))).thenReturn(dummyUser);
        when(userMapper.toResponse(dummyUser)).thenReturn(dummyUserResponse);

        UserResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.phone()).isEqualTo(TEST_PHONE);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getPassword()).isEqualTo(ENCODED_PASSWORD);
        assertThat(savedUser.getRole()).isEqualTo(UserRole.CLIENT);
    }

    @Test
    void register_PhoneAlreadyExists_ThrowsAccessDeniedException() {
        RegisterRequest request = new RegisterRequest(TEST_PHONE, "Test Name", TEST_PASSWORD);
        when(userRepository.existsByPhone(TEST_PHONE)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User has already registered!");

        verify(userRepository, never()).save(any());
    }


    @Test
    void venueRegister_Success_SavesVenueOwnerAndReturnsResponse() {
        RegisterRequest request = new RegisterRequest(TEST_PHONE, "Test Name", TEST_PASSWORD);

        when(userRepository.existsByPhone(TEST_PHONE)).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(dummyUser);
        when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(User.class))).thenReturn(dummyUser);
        when(userMapper.toResponse(dummyUser)).thenReturn(dummyUserResponse);

        UserResponse response = authService.venueRegister(request);

        assertThat(response).isNotNull();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getPassword()).isEqualTo(ENCODED_PASSWORD);
        assertThat(savedUser.getRole()).isEqualTo(UserRole.VENUE_OWNER);
    }

    @Test
    void venueRegister_PhoneAlreadyExists_ThrowsAccessDeniedException() {
        RegisterRequest request = new RegisterRequest(TEST_PHONE, TEST_PASSWORD, "Venue Name");
        when(userRepository.existsByPhone(TEST_PHONE)).thenReturn(true);

        assertThatThrownBy(() -> authService.venueRegister(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Venue owner has already registered!");

        verify(userRepository, never()).save(any());
    }


    @Test
    void getMe_Success_ReturnsCurrentUserResponse() {
        when(userDetailsService.getCurrentUser()).thenReturn(dummyUser);
        when(userMapper.toResponse(dummyUser)).thenReturn(dummyUserResponse);

        UserResponse response = authService.getMe();

        assertThat(response).isNotNull();
        assertThat(response.phone()).isEqualTo(TEST_PHONE);
        verify(userDetailsService).getCurrentUser();
        verify(userMapper).toResponse(dummyUser);
    }
}