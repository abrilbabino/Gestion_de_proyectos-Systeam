CREATE TABLE user_referral_codes (
    user_id    BIGINT PRIMARY KEY,
    code       VARCHAR(10) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE referrals (
    id          BIGSERIAL PRIMARY KEY,
    referrer_id BIGINT NOT NULL,
    referee_id  BIGINT NOT NULL UNIQUE,
    code        VARCHAR(10) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_streaks (
    user_id        BIGINT PRIMARY KEY,
    current_streak INT NOT NULL DEFAULT 0,
    longest_streak INT NOT NULL DEFAULT 0,
    last_check_in  DATE,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
