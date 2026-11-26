-- =====================================================================
-- Сроки этапов доставки (delivery.timeouts.*).
--
--   SEARCHING_COURIER — никто не взял заказ: доставка отменяется, клиент забирает сам;
--   ASSIGNED          — заказ готов, а курьер не пришёл: курьера снимают, заказ снова в ленте;
--   PICKED_UP         — курьер не отдал заказ клиенту: проблема для администратора.
--
-- deadline_at — срок текущего этапа; NULL — срока нет.
-- =====================================================================

ALTER TABLE deliveries
    ADD COLUMN deadline_at TIMESTAMPTZ;

COMMENT ON COLUMN deliveries.deadline_at IS 'Срок текущего этапа доставки; NULL — срока нет';

-- Незавершённым доставкам — полный срок с момента выката, чтобы никого не снять задним числом
UPDATE deliveries
SET deadline_at = now() + CASE status
                              WHEN 'SEARCHING_COURIER' THEN interval '30 minutes'
                              WHEN 'ASSIGNED' THEN interval '20 minutes'
                              ELSE interval '60 minutes'
    END
WHERE status IN ('SEARCHING_COURIER', 'ASSIGNED', 'PICKED_UP');

-- Проверка просрочек раз в минуту смотрит только на незавершённые доставки со сроком
CREATE INDEX ix_deliveries_deadline ON deliveries (deadline_at)
    WHERE deadline_at IS NOT NULL AND status IN ('SEARCHING_COURIER', 'ASSIGNED', 'PICKED_UP');
