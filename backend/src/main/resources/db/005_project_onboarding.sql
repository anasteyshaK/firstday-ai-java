-- Additive migration. Existing employee plans, documents and messages are preserved.
BEGIN;

CREATE TABLE IF NOT EXISTS projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    is_demo BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS project_memberships (
    project_id UUID NOT NULL REFERENCES projects(id),
    employee_id UUID NOT NULL REFERENCES employees(id),
    project_role VARCHAR(30) NOT NULL CHECK (project_role IN ('ENGINEERING', 'QA', 'DEVOPS', 'GENERAL')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (project_id, employee_id)
);

CREATE TABLE IF NOT EXISTS project_onboarding_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    position INTEGER NOT NULL,
    stage VARCHAR(30) NOT NULL CHECK (stage IN ('CONTEXT', 'ENVIRONMENT', 'DELIVERY')),
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    document_path TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'TODO' CHECK (status IN ('TODO', 'IN_PROGRESS', 'BLOCKED', 'DONE')),
    blocker_note VARCHAR(1000) NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (project_id, employee_id) REFERENCES project_memberships(project_id, employee_id),
    UNIQUE (project_id, employee_id, position)
);

-- Project documents deliberately live outside the public corporate RAG corpus.
CREATE TABLE IF NOT EXISTS project_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id),
    file_path TEXT NOT NULL,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (project_id, file_path)
);

-- Complete the locally supplied history schema without replacing existing rows.
CREATE TABLE IF NOT EXISTS chat_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES employees(id),
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE chat_conversations ADD COLUMN IF NOT EXISTS project_id UUID REFERENCES projects(id);
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS sources JSONB NOT NULL DEFAULT '[]'::jsonb;
CREATE INDEX IF NOT EXISTS idx_project_memberships_employee ON project_memberships(employee_id);
CREATE INDEX IF NOT EXISTS idx_chat_project_employee ON chat_conversations(employee_id, project_id, updated_at);

COMMIT;
