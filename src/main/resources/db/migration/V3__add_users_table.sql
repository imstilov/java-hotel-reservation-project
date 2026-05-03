CREATE TABLE users (
                       id          BIGSERIAL    PRIMARY KEY,
                       email       VARCHAR(255) NOT NULL UNIQUE,
                       first_name  VARCHAR(100) NOT NULL,
                       last_name   VARCHAR(100),
                       created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE reservations
    ADD CONSTRAINT fk_reservations_user
        FOREIGN KEY (user_id) REFERENCES users(id);