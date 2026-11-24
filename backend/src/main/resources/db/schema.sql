
-- FirstDay AI - Database Schema
-- PostgreSQL 17 + pgvector

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Employees
CREATE TABLE IF NOT EXISTS employees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash TEXT,
    role VARCHAR(30) NOT NULL
        CHECK (role IN ('EMPLOYEE', 'ADMIN', 'HR')),
    job_title VARCHAR(100) NOT NULL,
    department VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Corporate documents
CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    file_path TEXT NOT NULL UNIQUE,
    category VARCHAR(100) NOT NULL,
    department VARCHAR(100),
    access_level VARCHAR(30) NOT NULL DEFAULT 'EMPLOYEE'
        CHECK (access_level IN ('EMPLOYEE', 'HR', 'ADMIN')),
    content_hash VARCHAR(64),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. RAG document chunks
CREATE TABLE IF NOT EXISTS document_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL
        REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0),
    content TEXT NOT NULL,
    embedding VECTOR(768),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (document_id, chunk_index)
);

-- 4. Onboarding plans
CREATE TABLE IF NOT EXISTS onboarding_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL UNIQUE
        REFERENCES employees(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS'
        CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. Onboarding tasks
CREATE TABLE IF NOT EXISTS onboarding_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id UUID NOT NULL
        REFERENCES onboarding_plans(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    day_number INTEGER NOT NULL
        CHECK (day_number BETWEEN 1 AND 30),
    status VARCHAR(30) NOT NULL DEFAULT 'TODO'
        CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE')),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 6. Access requests
CREATE TABLE IF NOT EXISTS access_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL
        REFERENCES employees(id) ON DELETE CASCADE,
    system_name VARCHAR(100) NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

-- 7. Chat messages
CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL
        REFERENCES employees(id) ON DELETE CASCADE,
    conversation_id UUID NOT NULL,
    sender VARCHAR(20) NOT NULL
        CHECK (sender IN ('USER', 'ASSISTANT')),
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_documents_category
    ON documents(category);

CREATE INDEX IF NOT EXISTS idx_chunks_document_id
    ON document_chunks(document_id);

CREATE INDEX IF NOT EXISTS idx_chunks_embedding
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_tasks_plan
    ON onboarding_tasks(plan_id);

CREATE INDEX IF NOT EXISTS idx_chat_conversation
    ON chat_messages(conversation_id, created_at);

CREATE INDEX IF NOT EXISTS idx_access_requests_employee
    ON access_requests(employee_id);
