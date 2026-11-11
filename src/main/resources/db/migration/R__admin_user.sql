-- =====================================================================
-- Создание/обновление администратора из переменных окружения.
--
-- Без администратора нельзя зарегистрировать владельца заведения
-- (POST /api/v1/auth/venue-register доступен только ADMIN).
--
-- Значения приходят из Flyway-плейсхолдеров (см. application.yml):
--   DIUM_ADMIN_PHONE          — телефон (логин) администратора
--   DIUM_ADMIN_PASSWORD_HASH  — BCrypt-хэш пароля (НЕ сам пароль)
--   DIUM_ADMIN_NAME           — отображаемое имя (необязательно)
--
-- Миграция repeatable: Flyway перезапускает её, когда меняются значения
-- плейсхолдеров. Если переменные не заданы — ничего не делает.
-- =====================================================================

DO
$$
    DECLARE
        v_phone TEXT := btrim('${admin_phone}');
        v_hash  TEXT := btrim('${admin_password_hash}');
        v_name  TEXT := coalesce(nullif(btrim('${admin_name}'), ''), 'Administrator');
    BEGIN
        IF v_phone = '' OR v_hash = '' THEN
            RAISE NOTICE 'DIUM_ADMIN_PHONE / DIUM_ADMIN_PASSWORD_HASH не заданы — администратор не создаётся';
            RETURN;
        END IF;

        IF v_hash !~ '^\$2[aby]\$\d{2}\$.{53}$' THEN
            RAISE EXCEPTION 'DIUM_ADMIN_PASSWORD_HASH должен быть BCrypt-хэшем ($2a$/$2b$/$2y$)';
        END IF;

        INSERT INTO users (phone, name, password, role, is_confirmed)
        VALUES (v_phone, v_name, v_hash, 'ADMIN', TRUE)
        ON CONFLICT (phone) DO UPDATE
            SET password     = EXCLUDED.password,
                name         = EXCLUDED.name,
                role         = 'ADMIN',
                is_confirmed = TRUE;
    END
$$;
