-- =====================================================================
-- Индексы под реальные запросы репозиториев + индексы на все FK
-- (PostgreSQL не создаёт индексы на внешние ключи автоматически).
-- users.phone и venues.owner_id уже проиндексированы UNIQUE-ограничениями.
-- =====================================================================

-- products ------------------------------------------------------------
-- ProductRepository.findAllByVenueIdAndCategory + FK venue_id
CREATE INDEX ix_products_venue_category ON products (venue_id, category);
-- ProductRepository.findAllByVenueIdAndDeletedFalse (меню заведения)
CREATE INDEX ix_products_venue_active ON products (venue_id) WHERE deleted = FALSE;

-- modifier_groups / modifiers -----------------------------------------
-- ModifierGroupRepository.findAllByProductIdAndIsDeletedFalse + FK
CREATE INDEX ix_modifier_groups_product ON modifier_groups (product_id);
-- ModifierRepository.findAllByModifierGroupId + FK
CREATE INDEX ix_modifiers_group ON modifiers (modifier_group_id);

-- orders --------------------------------------------------------------
-- OrderRepository.findAllByUserIdOrderByCreatedAtDesc ("мои заказы")
CREATE INDEX ix_orders_user_created ON orders (user_id, created_at DESC);
-- findAllByVenueId...OrderByCreatedAtDesc (кухня и история заведения)
CREATE INDEX ix_orders_venue_status_created ON orders (venue_id, status, created_at DESC);
-- OrderExpirationService: PENDING-заказы старше N минут (частичный индекс, 0 = PENDING)
CREATE INDEX ix_orders_pending_created ON orders (created_at) WHERE status = 0;
-- OrderRepository.findByPickupCode. Не UNIQUE: формула кода повторяется каждые 9000 заказов
CREATE INDEX ix_orders_pickup_code ON orders (pickup_code);

-- order_items / order_item_modifiers ----------------------------------
CREATE INDEX ix_order_items_order ON order_items (order_id);
CREATE INDEX ix_order_items_product ON order_items (product_id);
-- PK (order_item_id, modifier_id) покрывает поиск по order_item_id
CREATE INDEX ix_order_item_modifiers_modifier ON order_item_modifiers (modifier_id);
