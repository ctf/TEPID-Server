--
-- Apparently renaming a column is different for different databases? This works with PostgreSQL
--

alter table printjob rename column queuename to queueid;