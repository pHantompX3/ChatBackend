/*
  Run this once as a sysadmin login (for example sa) before app startup.
  This bootstrap step creates the target database so Flyway can connect to it.
*/
IF DB_ID(N'wl_chat') IS NULL
BEGIN
    CREATE DATABASE [wl_chat];
END;
