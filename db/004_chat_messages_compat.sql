-- Preserve existing chat_messages rows. Add only missing sources column.
-- Run in firstday_db (public schema).
BEGIN;
ALTER TABLE public.chat_messages
    ADD COLUMN IF NOT EXISTS sources JSONB NOT NULL DEFAULT '[]'::jsonb;
COMMIT;
