-- Rotate the default admin password away from the weak, publicly-known 'Admin@123456'.
-- New initial password: SkyBooker@Init2026!
-- Deployers MUST change this password immediately after first login
-- (see docs/11_DEPLOYMENT_GUIDE.md).
UPDATE users
SET password_hash = '$2a$12$Ic4ixEiYwqRH3baoMujXqOxcU1vsEJnijrhPXH6S5YAdmj7Xnb91u'
WHERE email = 'admin@skybooker.local';
