--
-- harmonise FullUser._id to FullUser.shortUser
-- shortUser was the Primary Key, after all
--

SET session_replication_role = 'replica';

begin;
update fulluser_semesters set fulluser__id = fulluser.shortuser from fulluser where fulluser._id = fulluser__id;
update fulluser_groups set fulluser__id = fulluser.shortuser from fulluser where fulluser._id = fulluser__id;
update fullsession set user__id = fulluser.shortuser from fulluser where fulluser._id = user__id;
update fulluser set _id = shortuser;
commit;

SET session_replication_role = 'origin';
