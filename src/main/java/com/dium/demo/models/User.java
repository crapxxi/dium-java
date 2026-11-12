package com.dium.demo.models;


import com.dium.demo.enums.TwoFactorMethod;
import com.dium.demo.enums.UserRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String phone;

    @Column(nullable = false)
    private String name;

    @ToString.Exclude
    private String password;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    private String paymentFrom;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isConfirmed = false;

    // --- Двухфакторная аутентификация ---
    // Колонки nullable, чтобы ddl-auto=update добавил их в существующую таблицу; null = «выключено».

    @Builder.Default
    private Boolean twoFactorEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private TwoFactorMethod twoFactorMethod;

    /** TOTP-секрет, зашифрованный AES-256-GCM (см. SecretEncryptor). В открытом виде не хранится. */
    @ToString.Exclude
    @Column(length = 512)
    private String totpSecret;

    /** BCrypt-хэши одноразовых резервных кодов, по одному на строку. Сами коды не хранятся. */
    @ToString.Exclude
    @Column(length = 2000)
    private String backupCodeHashes;

    /** 2FA включена пользователем (обязательность для роли проверяется отдельно в TwoFactorService). */
    public boolean hasTwoFactor() {
        return Boolean.TRUE.equals(twoFactorEnabled) && twoFactorMethod != null;
    }


    @Override
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    @NonNull
    public String getUsername() {
        return phone;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return isConfirmed;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
