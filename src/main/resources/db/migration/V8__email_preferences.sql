-- Email reminders and the weekly summary. Both are opt-in (off by default). email_token is a random value
-- for the one-click unsubscribe link, made when the first email is sent. The last_* times stop repeats
-- after a restart or redeploy.
ALTER TABLE app_user ADD COLUMN email_reminders BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE app_user ADD COLUMN email_weekly BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE app_user ADD COLUMN email_token VARCHAR(43);
ALTER TABLE app_user ADD COLUMN last_reminder_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE app_user ADD COLUMN last_weekly_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE app_user ADD CONSTRAINT uk_app_user_email_token UNIQUE (email_token);
