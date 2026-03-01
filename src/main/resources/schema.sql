CREATE TABLE IF NOT EXISTS vacancy (
    id BIGINT PRIMARY KEY,
    title VARCHAR(500),
    company VARCHAR(255),
    salary VARCHAR(255),
    requirements TEXT,
    city VARCHAR(255),
    posted_date TIMESTAMP,
    source VARCHAR(50),
    url VARCHAR(500),
    parsed_date TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_vacancy_company ON vacancy(company);
CREATE INDEX IF NOT EXISTS idx_vacancy_city ON vacancy(city);
CREATE INDEX IF NOT EXISTS idx_vacancy_posted_date ON vacancy(posted_date);