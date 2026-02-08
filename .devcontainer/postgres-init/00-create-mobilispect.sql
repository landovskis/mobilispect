DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_database WHERE datname = 'mobilispect') THEN
    CREATE DATABASE mobilispect;
  END IF;
END $$;

GRANT ALL PRIVILEGES ON DATABASE mobilispect TO airflow;
