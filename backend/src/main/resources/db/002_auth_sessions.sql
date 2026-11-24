-- Run ONCE in pgAdmin Query Tool for firstday_db.
-- Do not drop existing tables or data.
CREATE TABLE IF NOT EXISTS auth_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_employee ON auth_sessions(employee_id);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_active ON auth_sessions(token_hash, expires_at)
    WHERE revoked_at IS NULL;
