-- Sandbox-Datenbanken für lokale Restore-Tests.
-- Eine Datenbank pro überwachter Service-Instanz – der Schema-Reset
-- (DROP SCHEMA public CASCADE) läuft je Instanz isoliert.

CREATE DATABASE sandbox_instance1;
CREATE DATABASE sandbox_instance2;
