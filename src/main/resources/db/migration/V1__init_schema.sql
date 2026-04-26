CREATE TABLE reservations (
                              id          BIGSERIAL PRIMARY KEY,
                              user_id     BIGINT NOT NULL,
                              room_id     BIGINT NOT NULL,
                              start_date  DATE   NOT NULL,
                              end_date    DATE   NOT NULL,
                              status      VARCHAR(255) NOT NULL,

                              CONSTRAINT reservations_status_check
                                  CHECK (status IN ('PENDING', 'APPROVED', 'CANCELED')),

                              CONSTRAINT reservations_dates_check
                                  CHECK (end_date >= start_date)
);

COMMENT ON TABLE reservations IS 'Бронирования номеров отеля';
COMMENT ON COLUMN reservations.status IS 'Статус: PENDING, APPROVED, CANCELED';