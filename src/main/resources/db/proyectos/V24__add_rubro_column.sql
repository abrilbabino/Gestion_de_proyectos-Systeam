-- V24: Add rubro column to projects table for dividend categorization
ALTER TABLE projects ADD COLUMN IF NOT EXISTS rubro INTEGER NOT NULL DEFAULT 4;
CREATE INDEX IF NOT EXISTS idx_projects_rubro ON projects(rubro);
