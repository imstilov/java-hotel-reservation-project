-- Индекс для запросов "мои бронирования"
-- Используется в API: GET /api/users/{userId}/reservations
CREATE INDEX IF NOT EXISTS idx_reservations_user_id
    ON reservations(user_id);

-- Composite-индекс: проверка доступности комнаты
-- Используется в логике подтверждения бронирования (overlap-проверка)
-- Покрывает запросы: WHERE room_id = ? AND start_date <= ? AND end_date >= ?
CREATE INDEX IF NOT EXISTS idx_reservations_room_dates
    ON reservations(room_id, start_date, end_date);

-- Partial-индекс: только активные бронирования
-- Меньше по размеру, быстрее для админских фильтров и отчётов
-- Бронирования со статусом CANCELED не входят в индекс — экономия места
CREATE INDEX IF NOT EXISTS idx_reservations_active
    ON reservations(start_date, end_date)
    WHERE status IN ('PENDING', 'APPROVED');