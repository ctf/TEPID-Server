--
-- Remove semesters from non-users
-- I know that this isn't perfect, but it is at least as good as what we have now
--

--
-- Want to manually explore your data before clobbering it?
-- Select users with no role:
-- select _id from FullUser where role = '';
--
-- identify semesters which will be deleted:
-- select * from fulluser_semesters where exists ( select 1 from fulluser where role = '' and fulluser_semesters.fulluser__id=fulluser._id);
--

delete from fulluser_semesters where exists ( select 1 from fulluser where role = '' and fulluser_semesters.fulluser__id=fulluser._id);

