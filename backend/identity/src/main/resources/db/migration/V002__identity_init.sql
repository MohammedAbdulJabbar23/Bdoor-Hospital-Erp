-- Identity: users + their roles.

CREATE TABLE app_user (
    id              UUID PRIMARY KEY,
    username        VARCHAR(100)  NOT NULL,
    password_hash   VARCHAR(200)  NOT NULL,
    full_name       VARCHAR(200)  NOT NULL,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL,
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT uk_user_username UNIQUE (username)
);

CREATE TABLE user_role (
    user_id   UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role      VARCHAR(50)  NOT NULL,
    PRIMARY KEY (user_id, role)
);

-- Initial admin user is seeded by DevDataSeeder in the app module on first boot,
-- using the configured PasswordEncoder so the bcrypt hash matches the active config.
