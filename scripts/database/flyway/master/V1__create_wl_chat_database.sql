/*
  Creates the wl_chat database if it does not already exist.
  Executed against the master database by Flyway bootstrap scripts.
*/
IF DB_ID(N'wl_chat') IS NULL
BEGIN
    CREATE DATABASE [wl_chat];
END;
