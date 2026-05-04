CREATE DATABASE IF NOT EXISTS autoport_db
DEFAULT CHARACTER SET utf8mb4
DEFAULT COLLATE utf8mb4_unicode_ci;

USE autoport_db;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NULL,
    name VARCHAR(100) NOT NULL,
    bio TEXT NULL,
    provider VARCHAR(20) NOT NULL,
    github_id VARCHAR(100) NULL UNIQUE,
    github_login VARCHAR(100) NULL,
    github_access_token TEXT NULL,
    profile_image TEXT NULL,
    role VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS temp_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    github_id VARCHAR(100) NOT NULL,
    github_login VARCHAR(100) NULL,
    github_access_token TEXT NULL,
    email VARCHAR(255) NOT NULL,
    profile_image TEXT NULL,
    expired_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS email_verifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL,
    expired_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS portfolio_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    preview_image_url TEXT NULL,
    is_active BOOLEAN NOT NULL,
    created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS portfolios (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    bio TEXT NOT NULL,
    is_public BOOLEAN NOT NULL,
    featured_project_id BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS portfolio_projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    portfolio_id BIGINT NOT NULL,
    repo_id BIGINT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    github_url TEXT NULL,
    display_order INT NULL,
    tech_stacks_json TEXT NULL,
    highlights_json TEXT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

-- FK
ALTER TABLE portfolios
ADD CONSTRAINT fk_portfolios_user
FOREIGN KEY (user_id) REFERENCES users(id)
ON DELETE CASCADE;

ALTER TABLE portfolios
ADD CONSTRAINT fk_portfolios_template
FOREIGN KEY (template_id) REFERENCES portfolio_templates(id);

ALTER TABLE portfolio_projects
ADD CONSTRAINT fk_portfolio_projects_portfolio
FOREIGN KEY (portfolio_id) REFERENCES portfolios(id)
ON DELETE CASCADE;

ALTER TABLE portfolios
ADD CONSTRAINT fk_portfolios_featured_project
FOREIGN KEY (featured_project_id) REFERENCES portfolio_projects(id);

-- 기본 템플릿
INSERT INTO portfolio_templates (
    name,
    description,
    preview_image_url,
    is_active,
    created_at
) VALUES (
    '기본 템플릿',
    '기본 포트폴리오 템플릿',
    NULL,
    true,
    NOW()
);
