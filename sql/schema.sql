CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255),
    name VARCHAR(100) NOT NULL,
    bio TEXT,
    provider VARCHAR(20) NOT NULL,
    github_id VARCHAR(100) UNIQUE,
    github_login VARCHAR(100),
    github_access_token TEXT,
    profile_image TEXT,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS temp_users (
    id BIGSERIAL PRIMARY KEY,
    github_id VARCHAR(100) NOT NULL,
    github_login VARCHAR(100),
    github_access_token TEXT,
    email VARCHAR(255) NOT NULL,
    profile_image TEXT,
    expired_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS email_verifications (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL,
    expired_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS portfolio_templates (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    preview_image_url TEXT,
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS portfolios (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    template_id BIGINT,
    title VARCHAR(255) NOT NULL,
    bio TEXT NOT NULL,
    summary TEXT,
    description TEXT,
    technical_contributions_json TEXT,
    code_highlights_json TEXT,
    project_links_json TEXT,
    generated_at TEXT,
    is_public BOOLEAN NOT NULL,
    featured_project_id BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS portfolio_projects (
    id BIGSERIAL PRIMARY KEY,
    portfolio_id BIGINT NOT NULL,
    repo_id BIGINT,
    name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    one_line_description TEXT,
    estimated_period TEXT,
    role TEXT,
    github_url TEXT,
    deploy_url TEXT,
    display_order INTEGER,
    tech_stacks_json TEXT,
    main_features_json TEXT,
    highlights_json TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

DO $$
DECLARE
    portfolio_project_fk_name TEXT;
BEGIN
    ALTER TABLE portfolios
    ALTER COLUMN template_id DROP NOT NULL;

    ALTER TABLE portfolios
    ADD COLUMN IF NOT EXISTS summary TEXT;

    ALTER TABLE portfolios
    ADD COLUMN IF NOT EXISTS description TEXT;

    ALTER TABLE portfolios
    ADD COLUMN IF NOT EXISTS technical_contributions_json TEXT;

    ALTER TABLE portfolios
    ADD COLUMN IF NOT EXISTS code_highlights_json TEXT;

    ALTER TABLE portfolios
    ADD COLUMN IF NOT EXISTS project_links_json TEXT;

    ALTER TABLE portfolios
    ADD COLUMN IF NOT EXISTS generated_at TEXT;

    ALTER TABLE portfolio_projects
    ADD COLUMN IF NOT EXISTS one_line_description TEXT;

    ALTER TABLE portfolio_projects
    ADD COLUMN IF NOT EXISTS estimated_period TEXT;

    ALTER TABLE portfolio_projects
    ADD COLUMN IF NOT EXISTS role TEXT;

    ALTER TABLE portfolio_projects
    ADD COLUMN IF NOT EXISTS deploy_url TEXT;

    ALTER TABLE portfolio_projects
    ADD COLUMN IF NOT EXISTS main_features_json TEXT;

    FOR portfolio_project_fk_name IN
        SELECT constraint_name
        FROM information_schema.key_column_usage
        WHERE table_schema = current_schema()
          AND table_name = 'portfolio_projects'
          AND column_name = 'portfolio_id'
          AND position_in_unique_constraint IS NOT NULL
    LOOP
        EXECUTE format(
            'ALTER TABLE portfolio_projects DROP CONSTRAINT IF EXISTS %I',
            portfolio_project_fk_name
        );
    END LOOP;

    ALTER TABLE portfolio_projects
    ADD CONSTRAINT fk_portfolio_projects_portfolio
    FOREIGN KEY (portfolio_id) REFERENCES portfolios(id)
    ON DELETE CASCADE;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_portfolios_user'
    ) THEN
        ALTER TABLE portfolios
        ADD CONSTRAINT fk_portfolios_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_portfolios_template'
    ) THEN
        ALTER TABLE portfolios
        ADD CONSTRAINT fk_portfolios_template
        FOREIGN KEY (template_id) REFERENCES portfolio_templates(id);
    END IF;

    UPDATE portfolios
    SET featured_project_id = NULL
    WHERE featured_project_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1
          FROM portfolio_projects
          WHERE portfolio_projects.id = portfolios.featured_project_id
      );

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_portfolios_featured_project'
    ) THEN
        ALTER TABLE portfolios
        ADD CONSTRAINT fk_portfolios_featured_project
        FOREIGN KEY (featured_project_id) REFERENCES portfolio_projects(id)
        ON DELETE SET NULL;
    END IF;
END $$;

INSERT INTO portfolio_templates (
    name,
    description,
    preview_image_url,
    is_active,
    created_at
)
SELECT
    '기본 템플릿',
    '기본 포트폴리오 템플릿',
    NULL,
    TRUE,
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM portfolio_templates WHERE name = '기본 템플릿'
);
