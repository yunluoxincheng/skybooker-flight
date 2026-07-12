-- users.real_name was a legacy account field and must not be confused with passenger.name.
-- Do not migrate its values to passenger.name: an account may manage multiple passengers.
ALTER TABLE users DROP COLUMN real_name;
