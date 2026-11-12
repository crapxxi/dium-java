-- =====================================================================
-- Двухфакторная аутентификация: колонки сущности User, которых не было
-- в базовой схеме. Без них Hibernate (ddl-auto=validate) не запускается.
--
-- Все колонки nullable: у существующих пользователей 2FA выключена
-- (NULL в two_factor_enabled трактуется как «выключено», см. User.hasTwoFactor()).
--
-- IF NOT EXISTS: в базах, где колонки уже добавлены вручную, миграция не упадёт.
-- =====================================================================

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS two_factor_method  VARCHAR(16),
    ADD COLUMN IF NOT EXISTS totp_secret        VARCHAR(512),
    ADD COLUMN IF NOT EXISTS backup_code_hashes VARCHAR(2000);

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS ck_users_two_factor_method;

ALTER TABLE users
    ADD CONSTRAINT ck_users_two_factor_method
        CHECK (two_factor_method IS NULL OR two_factor_method IN ('WHATSAPP', 'TOTP'));

COMMENT ON COLUMN users.two_factor_enabled IS '2FA включена пользователем; NULL = выключена';
COMMENT ON COLUMN users.two_factor_method IS 'Способ второго фактора (enum TwoFactorMethod)';
COMMENT ON COLUMN users.totp_secret IS 'TOTP-секрет, зашифрованный AES-256-GCM; в открытом виде не хранится';
COMMENT ON COLUMN users.backup_code_hashes IS 'BCrypt-хэши одноразовых резервных кодов, по одному на строку';
