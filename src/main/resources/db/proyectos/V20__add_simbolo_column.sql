ALTER TABLE projects ADD COLUMN IF NOT EXISTS simbolo VARCHAR(5);
ALTER TABLE subtokens ADD COLUMN IF NOT EXISTS simbolo VARCHAR(5);

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_projects_simbolo') THEN
    ALTER TABLE projects ADD CONSTRAINT uq_projects_simbolo UNIQUE (simbolo);
  END IF;
END $$;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_subtokens_simbolo') THEN
    ALTER TABLE subtokens ADD CONSTRAINT uq_subtokens_simbolo UNIQUE (simbolo);
  END IF;
END $$;
