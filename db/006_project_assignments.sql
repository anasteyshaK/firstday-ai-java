-- Additive: preserve existing profiles, memberships, tasks and conversations.
BEGIN;
ALTER TABLE employees ADD COLUMN IF NOT EXISTS employment_type VARCHAR(20) NOT NULL DEFAULT 'EMPLOYEE'
    CHECK (employment_type IN ('INTERN', 'EMPLOYEE'));
-- COMPANY preserves the start screen of existing accounts; new registration chooses explicitly.
ALTER TABLE employees ADD COLUMN IF NOT EXISTS onboarding_mode VARCHAR(20) NOT NULL DEFAULT 'COMPANY'
    CHECK (onboarding_mode IN ('COMPANY', 'PROJECT'));
ALTER TABLE project_memberships ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE project_memberships ADD COLUMN IF NOT EXISTS assigned_by UUID REFERENCES employees(id);
ALTER TABLE project_memberships ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
COMMIT;
