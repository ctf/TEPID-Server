-- groups

begin;

alter table fulluser_groups
drop constraint fkd7o6sldgm41qrbqea4l7muk2q;

alter table fulluser_groups
add constraint fkd7o6sldgm41qrbqea4l7muk2q
foreign key (fulluser__id)
references fulluser(_id)
on delete cascade;

commit;

-- semesters

begin;

alter table fulluser_semesters
drop constraint fk7boa81tl3qqsoh85n6raknp63;

alter table fulluser_semesters
add constraint fk7boa81tl3qqsoh85n6raknp63
foreign key (fulluser__id)
references fulluser(_id)
on delete cascade;

commit;

-- sessions

begin;

alter table fullsession
drop constraint fkrpitgjja572pxckjjev8o5w2g;

alter table fullsession
add constraint fkrpitgjja572pxckjjev8o5w2g
foreign key (user__id)
references fulluser(_id)
on delete cascade;

commit;