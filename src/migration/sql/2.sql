-- Converts Courses in Semesters

insert into fulluser_semesters
select fulluser__id, season, year from fulluser_courses;
