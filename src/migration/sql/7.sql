--
-- changes the semantics of the PrintJob.userIdentification field to use the FullUser._id instead of the FullUser.shortUser
--

update printjob set useridentification = fulluser._id from fulluser where shortuser = useridentification;