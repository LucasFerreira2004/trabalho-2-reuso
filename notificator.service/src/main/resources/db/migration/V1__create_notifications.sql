CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    source VARCHAR(120) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    recipient VARCHAR(320) NOT NULL,
    subject VARCHAR(255),
    message TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    error_message VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE
);
