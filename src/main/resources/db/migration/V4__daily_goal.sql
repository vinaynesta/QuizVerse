-- Quizzes per day the user aims for (streaks and daily goals on My scores).
ALTER TABLE app_user ADD COLUMN daily_goal INTEGER DEFAULT 2 NOT NULL;
