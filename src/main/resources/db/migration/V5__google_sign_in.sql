-- Google sign-in: the Google account's stable id (the ID token's "sub"). Accounts created with Google
-- have no password until the user sets one on the profile page.
ALTER TABLE app_user ADD COLUMN google_subject VARCHAR(255);
ALTER TABLE app_user ADD CONSTRAINT uk_app_user_google UNIQUE (google_subject);
ALTER TABLE app_user ALTER COLUMN password_hash DROP NOT NULL;
