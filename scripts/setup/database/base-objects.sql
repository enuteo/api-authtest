CREATE TABLE IF NOT EXISTS base_objects (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

INSERT INTO base_objects (id, name)
VALUES
    (1, 'First object'),
    (2, 'Second object'),
    (3, 'Third object')
ON CONFLICT (id) DO NOTHING;
