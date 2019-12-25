# 2.5
## !154: Refactor Queue identification
- Change PrintJob.queueName to PrintJob.queueId

- Changed semantics of this field to point to PrintQueue._id

- Changed semantics of printing to use the PrintQueue_id and not the PrintQueue.name, and to no longer require coupling between them.

- **DB** apply migration 6 

## !128

- setNick, setJobExpiration, setcolor return a PutResponse, instead of a FullUser
- deprecate useless adminConfigured endpoint

- changes the semantics of the PrintJob.userIdentification field to use the FullUser._id instead of the FullUser.shortUser