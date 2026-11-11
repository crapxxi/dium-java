-- =====================================================================
-- ТЕСТОВЫЕ ДАННЫЕ — ТОЛЬКО ДЛЯ DEV-ПРОФИЛЯ
-- Подключается через spring.flyway.locations в application-dev.yml.
-- В prod этот каталог не сканируется.
--
-- Пользователи (пароль у всех: password123):
--   77000000000 — ADMIN
--   77000000001 — VENUE_OWNER (владелец "DIUM Coffee")
--   77000000002 — CLIENT
--
-- Скрипт идемпотентен: повторный запуск не создаёт дублей.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Пользователи --------------------------------------------------------
INSERT INTO users (phone, name, password, role, is_confirmed)
VALUES ('77000000000', 'Dev Admin', crypt('password123', gen_salt('bf', 10)), 'ADMIN', TRUE),
       ('77000000001', 'Dev Owner', crypt('password123', gen_salt('bf', 10)), 'VENUE_OWNER', TRUE),
       ('77000000002', 'Dev Client', crypt('password123', gen_salt('bf', 10)), 'CLIENT', TRUE)
ON CONFLICT (phone) DO NOTHING;

-- Заведение -----------------------------------------------------------
INSERT INTO venues (owner_id, name, description, address, can_deliver, delivery_price, kaspi_url, is_working)
SELECT u.id,
       'DIUM Coffee',
       'Кофейня на первом этаже главного корпуса',
       'Главный корпус, 1 этаж',
       TRUE,
       300,
       'https://pay.kaspi.kz/pay/dev-demo',
       TRUE
FROM users u
WHERE u.phone = '77000000001'
ON CONFLICT (owner_id) DO NOTHING;

-- Меню ----------------------------------------------------------------
INSERT INTO products (venue_id, name, description, price, category, in_stock, has_modifiers)
SELECT v.id, p.name, p.description, p.price, p.category, TRUE, p.has_modifiers
FROM venues v
         JOIN users u ON u.id = v.owner_id AND u.phone = '77000000001'
         -- category: 0 = FOOD, 1 = DRINK (ORDINAL)
         CROSS JOIN (VALUES ('Капучино', 'Двойной эспрессо и молоко', 1200.00, 1, TRUE),
                            ('Американо', 'Эспрессо и горячая вода', 900.00, 1, FALSE),
                            ('Сэндвич с курицей', 'Курица, сыр, свежие овощи', 1800.00, 0, TRUE),
                            ('Круассан', 'Сливочный круассан', 700.00, 0, FALSE))
    AS p(name, description, price, category, has_modifiers)
WHERE NOT EXISTS (SELECT 1 FROM products x WHERE x.venue_id = v.id AND x.name = p.name);

-- Группы модификаторов ------------------------------------------------
INSERT INTO modifier_groups (product_id, name, is_required, min_choices, max_choices)
SELECT pr.id, g.group_name, g.is_required, g.min_choices, g.max_choices
FROM products pr
         JOIN venues v ON v.id = pr.venue_id
         JOIN users u ON u.id = v.owner_id AND u.phone = '77000000001'
         JOIN (VALUES ('Капучино', 'Размер', TRUE, 1, 1),
                      ('Капучино', 'Сироп', FALSE, 0, 2),
                      ('Сэндвич с курицей', 'Соус', FALSE, 0, 1))
    AS g(product_name, group_name, is_required, min_choices, max_choices)
              ON g.product_name = pr.name
WHERE NOT EXISTS (SELECT 1 FROM modifier_groups x WHERE x.product_id = pr.id AND x.name = g.group_name);

-- Модификаторы --------------------------------------------------------
INSERT INTO modifiers (modifier_group_id, name, price_delta)
SELECT mg.id, m.modifier_name, m.price_delta
FROM modifier_groups mg
         JOIN products pr ON pr.id = mg.product_id
         JOIN venues v ON v.id = pr.venue_id
         JOIN users u ON u.id = v.owner_id AND u.phone = '77000000001'
         JOIN (VALUES ('Капучино', 'Размер', 'S (250 мл)', 0.00),
                      ('Капучино', 'Размер', 'M (350 мл)', 200.00),
                      ('Капучино', 'Размер', 'L (450 мл)', 400.00),
                      ('Капучино', 'Сироп', 'Карамель', 150.00),
                      ('Капучино', 'Сироп', 'Ваниль', 150.00),
                      ('Сэндвич с курицей', 'Соус', 'Чесночный', 100.00),
                      ('Сэндвич с курицей', 'Соус', 'Барбекю', 100.00))
    AS m(product_name, group_name, modifier_name, price_delta)
              ON m.product_name = pr.name AND m.group_name = mg.name
WHERE NOT EXISTS (SELECT 1 FROM modifiers x WHERE x.modifier_group_id = mg.id AND x.name = m.modifier_name);
