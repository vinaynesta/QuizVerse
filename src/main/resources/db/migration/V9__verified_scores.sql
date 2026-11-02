-- Server-side scoring. verified: the server scored the attempt from its own answer key and a signed receipt
-- proves it was finished within the time limit; only verified attempts appear on leaderboards.
-- receipt_hash makes each receipt count once. leaderboard_hidden shows the user as "Anonymous" on boards.
ALTER TABLE quiz_attempt ADD COLUMN verified BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE quiz_attempt ADD COLUMN receipt_hash VARCHAR(64);
ALTER TABLE quiz_attempt ADD CONSTRAINT uk_attempt_receipt UNIQUE (receipt_hash);
CREATE INDEX idx_attempt_board ON quiz_attempt (verified, exam_id, stage_id, section_id, level_id, quiz_index);
CREATE INDEX idx_attempt_verified_time ON quiz_attempt (verified, completed_at);
ALTER TABLE app_user ADD COLUMN leaderboard_hidden BOOLEAN DEFAULT FALSE NOT NULL;
