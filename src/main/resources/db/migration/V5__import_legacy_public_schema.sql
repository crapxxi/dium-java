-- =====================================================================
-- Перенос данных из старой схемы "public" (создана Hibernate через
-- ddl-auto=update, до появления Flyway) в схему приложения "dium".
--
-- * Если в public нет старых таблиц (новая БД) — миграция ничего не делает.
-- * Старые таблицы НЕ изменяются и НЕ удаляются: при проблеме можно
--   откатиться на прошлую версию приложения, она продолжит работать с public.
--   Удалить их можно отдельной миграцией после проверки.
-- * id переносятся как есть (ссылки между таблицами и номера заказов
--   сохраняются), затем счётчики identity сдвигаются за максимальный id.
-- * Пустые значения, которые старая схема допускала, а новая — нет,
--   заменяются теми же значениями по умолчанию, что ставит Java-код.
-- * Любое нарушение ограничений новой схемы останавливает миграцию целиком
--   (транзакция откатывается) — данные не теряются молча.
-- =====================================================================

DO
$$
    DECLARE
        v_users  BIGINT;
        v_venues BIGINT;
        v_orders BIGINT;
    BEGIN
        -- Миграции накатываются прямо в public (так делает, например, DevSeedMigrationTest):
        -- тогда public — это и есть новая схема, переносить нечего.
        IF current_schema() = 'public' THEN
            RAISE NOTICE 'Миграции применяются к схеме public — перенос не требуется';
            RETURN;
        END IF;

        IF to_regclass('public.users') IS NULL THEN
            RAISE NOTICE 'Старая схема public не найдена — перенос не требуется';
            RETURN;
        END IF;

        RAISE NOTICE 'Найдены старые таблицы в public — переносим данные в схему dium';

        -- users -------------------------------------------------------
        INSERT INTO users (id, phone, name, password, role, payment_from, is_confirmed)
        SELECT u.id,
               u.phone,
               coalesce(nullif(btrim(u.name), ''), 'Без имени'),
               coalesce(u.password, '!'), -- без пароля войти нельзя; '!' не совпадёт ни с одним BCrypt
               coalesce(u.role, 'CLIENT'),
               nullif(btrim(u.payment_from), ''),
               coalesce(u.is_confirmed, FALSE)
        FROM public.users u;

        -- Колонки 2FA (V4): переносим, только если они уже были в старой таблице
        IF EXISTS (SELECT 1
                   FROM information_schema.columns
                   WHERE table_schema = 'public'
                     AND table_name = 'users'
                     AND column_name = 'two_factor_enabled') THEN
            EXECUTE $sql$
                UPDATE users n
                SET two_factor_enabled = coalesce(o.two_factor_enabled, FALSE),
                    two_factor_method  = o.two_factor_method,
                    totp_secret        = o.totp_secret,
                    backup_code_hashes = o.backup_code_hashes
                FROM public.users o
                WHERE o.id = n.id
            $sql$;
        END IF;

        -- venues ------------------------------------------------------
        INSERT INTO venues (id, owner_id, name, description, address, image_url,
                            can_deliver, delivery_price, kaspi_url, is_working)
        SELECT v.id,
               v.owner_id,
               v.name,
               v.description,
               v.address,
               v.image_url,
               coalesce(v.can_deliver, FALSE),
               coalesce(v.delivery_price, 0),
               nullif(btrim(v.kaspi_url), ''),
               coalesce(v.is_working, TRUE)
        FROM public.venues v;

        -- products ----------------------------------------------------
        INSERT INTO products (id, venue_id, name, description, price, category,
                              image_url, in_stock, has_modifiers, deleted)
        SELECT p.id,
               p.venue_id,
               p.name,
               p.description,
               coalesce(p.price, 0),
               coalesce(p.category, 0),
               p.image_url,
               coalesce(p.in_stock, FALSE),
               coalesce(p.has_modifiers, FALSE),
               coalesce(p.deleted, FALSE)
        FROM public.products p;

        -- modifier_groups ---------------------------------------------
        INSERT INTO modifier_groups (id, product_id, name, is_required,
                                     min_choices, max_choices, is_deleted)
        SELECT g.id,
               g.product_id,
               coalesce(nullif(btrim(g.name), ''), 'Опции'),
               coalesce(g.is_required, FALSE),
               g.min_choices,
               -- старая валидация допускала min > max; поднимаем max до min
               CASE
                   WHEN g.min_choices IS NOT NULL AND g.max_choices IS NOT NULL
                       AND g.min_choices > g.max_choices THEN g.min_choices
                   ELSE g.max_choices
                   END,
               coalesce(g.is_deleted, FALSE)
        FROM public.modifier_groups g;

        -- modifiers ---------------------------------------------------
        INSERT INTO modifiers (id, modifier_group_id, name, price_delta, in_stock)
        SELECT m.id,
               m.modifier_group_id,
               coalesce(nullif(btrim(m.name), ''), 'Опция'),
               coalesce(m.price_delta, 0),
               coalesce(m.in_stock, TRUE)
        FROM public.modifiers m;

        -- orders ------------------------------------------------------
        INSERT INTO orders (id, user_id, venue_id, status, address, comment, delivery_fee,
                            total_sum, pickup_code, payment_from, created_at)
        SELECT o.id,
               o.user_id,
               o.venue_id,
               coalesce(o.status, 0),
               nullif(btrim(o.address), ''),
               o.comment,
               coalesce(o.delivery_fee, 0),
               o.total_sum,
               o.pickup_code,
               coalesce(nullif(btrim(o.payment_from), ''), nullif(btrim(u.payment_from), ''), 'не указан'),
               coalesce(o.created_at, now())
        FROM public.orders o
                 LEFT JOIN public.users u ON u.id = o.user_id;

        -- order_items -------------------------------------------------
        INSERT INTO order_items (id, order_id, product_id, count, price_at_purchase)
        SELECT i.id, i.order_id, i.product_id, i.count, i.price_at_purchase
        FROM public.order_items i;

        -- order_item_modifiers (в старой таблице не было PK — убираем дубли)
        IF to_regclass('public.order_item_modifiers') IS NOT NULL THEN
            INSERT INTO order_item_modifiers (order_item_id, modifier_id)
            SELECT DISTINCT x.order_item_id, x.modifier_id
            FROM public.order_item_modifiers x;
        END IF;

        -- Триггер на INSERT записал в журнал "создан со статусом X" с текущим
        -- временем — для старых заказов это неправда, настоящей истории нет.
        DELETE FROM order_status_history;

        -- Счётчики id продолжают нумерацию после перенесённых записей ------
        PERFORM setval(pg_get_serial_sequence('users', 'id'), coalesce(max(id), 0) + 1, FALSE) FROM users;
        PERFORM setval(pg_get_serial_sequence('venues', 'id'), coalesce(max(id), 0) + 1, FALSE) FROM venues;
        PERFORM setval(pg_get_serial_sequence('products', 'id'), coalesce(max(id), 0) + 1, FALSE) FROM products;
        PERFORM setval(pg_get_serial_sequence('modifier_groups', 'id'), coalesce(max(id), 0) + 1, FALSE) FROM modifier_groups;
        PERFORM setval(pg_get_serial_sequence('modifiers', 'id'), coalesce(max(id), 0) + 1, FALSE) FROM modifiers;
        PERFORM setval(pg_get_serial_sequence('orders', 'id'), coalesce(max(id), 0) + 1, FALSE) FROM orders;
        PERFORM setval(pg_get_serial_sequence('order_items', 'id'), coalesce(max(id), 0) + 1, FALSE) FROM order_items;

        SELECT count(*) INTO v_users FROM users;
        SELECT count(*) INTO v_venues FROM venues;
        SELECT count(*) INTO v_orders FROM orders;
        RAISE NOTICE 'Перенесено: пользователей %, заведений %, заказов %', v_users, v_venues, v_orders;
    END
$$;
