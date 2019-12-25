--
-- Apparently renaming a column is different for different databases? This works with PostgreSQL
--

alter table printjob rename column queuename to queueid;

--
-- Sets the PrintQueue _id to the name, since that's what it was functionally
--

begin;

ALTER TABLE printqueue_destinations drop constraint fkn0px4nk3bhbbmpeprf8jmx9bf;

update printqueue_destinations set printqueue__id = name from printqueue where _id = printqueue__id;

update printqueue set _id = name;

ALTER TABLE ONLY public.printqueue_destinations
    ADD CONSTRAINT fkn0px4nk3bhbbmpeprf8jmx9bf FOREIGN KEY (printqueue__id) REFERENCES public.printqueue(_id);

commit;
