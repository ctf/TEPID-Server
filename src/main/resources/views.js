{
  "_id": "_design/main",
  "_rev": "32-8430058110502c8460bc9f27efc89cb2",
  "language": "javascript",
  "views": {
    "byQueue": {
      "map": "function(doc) {\n  if (doc.queueName) {\n    emit(doc.queueName, doc);\n  }\n};"
    },
    "byType": {
      "map": "function(doc) {\n  if (doc.type) {\n    emit(doc.type, doc);\n  }\n};"
    },
    "byDigest": {
      "map": "function(doc) {\n  if (doc.type === 'job' && doc._attachments && doc._attachments.jobData && doc._attachments.jobData.digest) {\n    emit(doc._attachments.jobData.digest, doc);\n  }\n};"
    },
    "byUser": {
      "map": "function(doc) {\n  if (doc.userIdentification) {\n    emit(doc.userIdentification, doc);\n  }\n};"
    },
    "pageTotals": {
      "map": "function(doc) {\n  if (doc.userIdentification && doc.pages) {\n    emit(doc.userIdentification, doc.pages);\n  }\n};",
      "reduce": "_sum"
    },
    "quota": {
      "map": "function(doc) {\n  if (doc.userIdentification && doc.pages && !doc.refunded) {\nvar pages = doc.pages;\nif (doc.colorPages) pages = doc.pages - doc.colorPages + doc.colorPages * 3;\n    emit(doc.userIdentification, [pages, doc.started]);\n  }\n};",
      "reduce": "function(keys, values, rereduce) {\n\tvar sum = 0,\n\t\tearliestJob;\n\tfor (var i = 0; i < values.length; i++) {\n\t\tsum += values[i][0];\n\t\tif (!earliestJob || values[i][1] < earliestJob) {\n\t\t\tearliestJob = values[i][1];\n\t\t}\n\t}\n\tvar d1 = new Date(earliestJob),\n\t\tm1 = d1.getMonth() + 1,\n\t\ty1 = d1.getFullYear(),\n\t\td2 = new Date(),\n\t\tm2 = d2.getMonth() + 1,\n\t\ty2 = d2.getFullYear();\n\treturn (y2 - y1) * 1000 + ((m2 > 8 && (y1 !== y2 || m1 < 8)) ? 1000 : 500) - sum;\n}"
    },
    "users": {
      "map": "function(doc) {\n  if (doc.userIdentification) {\n    emit(doc.userIdentification, null);\n  }\n};",
      "reduce": "function(key, values) {\n    return null;\n}"
    },
    "typeKeys": {
      "map": "function(doc) {\n  if (doc.type) {\n    emit(doc.type, null);\n  }\n};",
      "reduce": "function(key, values) {\n    return null;\n}"
    },
    "types": {
      "map": "function(doc) {\n  if (doc.type) {\n    emit(doc.type, null);\n  }\n};",
      "reduce": "function(key, values) {\n    return null;\n}"
    },
    "queues": {
      "map": "function(doc) {\n  if (doc.type === 'queue') {\n    emit(doc.name, doc);\n  }\n};"
    },
    "destinations": {
      "map": "function(doc) {\n  if (doc.type === 'destination') {\n    emit(doc.name, doc);\n  }\n};"
    },
    "byLongUser": {
      "map": "function(doc) {\n  if (doc.type==='user') {\n    emit(doc.longUser, doc);\n  }\n};"
    },
    "byStudentId": {
      "map": "function(doc) {\n  if (doc.type==='user' && doc.studentId) {\n    emit(doc.studentId, doc);\n  }\n};"
    },
    "byProcessed": {
      "map": "function(doc) {\n  if (doc.queueName && doc.processed > 0) {\n    emit([doc.queueName, doc.processed], doc);\n  }\n};"
    },
    "sessions": {
      "map": "function(doc) {\n  if (doc.type === 'session') {\n    emit(doc._id, doc);\n  }\n};"
    },
    "signup": {
      "map": "function(doc) {\n  if (doc.type === 'signup') {\n    emit(doc._id, doc);\n  }\n};"
    },
    "checkin": {
      "map": "function(doc) {\n  if (doc.type === 'checkin') {\n    emit(doc._id, doc);\n  }\n};"
    },
    "maxEta": {
      "map": "function(doc) {\n  if (doc.destination) {\n    emit(doc.destination, doc.eta);\n  }\n};",
      "reduce": "function(keys, values) {\n  var max = -1;\n  for (var i = 0; i < values.length; i++) {\n    if (values[i] > max) max = values[i];\n  }\n  return max;\n}"
    },
    "signupByName": {
      "map": "function(doc) {\n  if (doc.type === 'signup') {\n    emit(doc.name, doc);\n  }\n};"
    },
    "onDuty": {
      "map": "function(doc) \n{\n\tif (doc.type ==='signup')\n\t{\n\t\tfor (i in doc.slots)\n\t\t{\n\t\t\tfor (j in doc.slots[i])\n\t\t\t{\n\t\t\t\temit(i + \"-\" + doc.slots[i][j] , doc);\n\t\t\t}\n\t\t}\n\t}\n}"
    },
    "localAdmins": {
      "map": "function(doc) {\n  if (doc.type==='user'&&doc.role==='admin') {\n    emit(doc._id, doc);\n  }\n};"
    },
    "oldJobs": {
      "map": "function(doc) {\n  if (doc.type==='job' && !doc.processed && !doc.failed) {\n    emit(doc.started, doc);\n  }\n};"
    },
    "storedJobs": {
      "map": "function(doc) {\n  if (doc.type==='job' && (!!doc.file || !!doc._attachments)) {\n    emit(doc._id, doc);\n  }\n};"
    },
    "emailReasons": {
      "map": "function(doc) {\n  if (doc.type==='emailReasons') {\n    emit(doc._id, doc);\n  }\n};"
    },
    "totalPrinted": {
      "map": "function(doc) {\n  if (doc.userIdentification && doc.pages && !doc.refunded) {\nvar pages = doc.pages;\nif (doc.colorPages) pages = doc.pages - doc.colorPages + doc.colorPages * 3;\n    if (doc.printed && doc.printed!=-1) emit(doc.userIdentification, [pages, doc.started]);\n  }\n};",
      "reduce": "function(keys, values, rereduce) {\n\tif (!rereduce) {\n\t\tvar sum = 0, earliestJob;\n\t\tfor (var i = 0; i < values.length; i++) {\n\t\t\tsum += values[i][0] || 0;\n\t\t\tif (!earliestJob || values[i][1] < earliestJob) {\n\t\t\t\tearliestJob = values[i][1];\n\t\t\t}\n\t\t}\n\t\treturn {sum: sum, earliestJob: earliestJob};\n\t} else {\n\t\tvar sum = 0, earliestJob;\n\t\tfor (var i = 0; i < values.length; i++) {\n\t\t\tsum += values[i].sum || 0;\n\t\t\tif (!earliestJob || values[i].earliestJob < earliestJob) {\n\t\t\t\tearliestJob = values[i].earliestJob;\n\t\t\t}\n\t\t}\n\t\treturn {sum: sum, earliestJob: earliestJob};\n\t}\n}"
    },
    "totalPrintedWithRefunds": {
      "map": "function(doc) {\n  if (doc.userIdentification && doc.pages) {\nvar pages = doc.pages;\nif (doc.colorPages) pages = doc.pages - doc.colorPages + doc.colorPages * 3;\n    if (doc.printed) emit(doc.userIdentification, [pages, doc.started]);\n  }\n};",
      "reduce": "function(keys, values, rereduce) {\n\tif (!rereduce) {\n\t\tvar sum = 0, earliestJob;\n\t\tfor (var i = 0; i < values.length; i++) {\n\t\t\tsum += values[i][0] || 0;\n\t\t\tif (!earliestJob || values[i][1] < earliestJob) {\n\t\t\t\tearliestJob = values[i][1];\n\t\t\t}\n\t\t}\n\t\treturn {sum: sum, earliestJob: earliestJob};\n\t} else {\n\t\tvar sum = 0, earliestJob;\n\t\tfor (var i = 0; i < values.length; i++) {\n\t\t\tsum += values[i].sum || 0;\n\t\t\tif (!earliestJob || values[i].earliestJob < earliestJob) {\n\t\t\t\tearliestJob = values[i].earliestJob;\n\t\t\t}\n\t\t}\n\t\treturn {sum: sum, earliestJob: earliestJob};\n\t}\n}"
    },
    "jobsByQueueAndTime": {
      "map": "function(doc) {\n  if (doc.queueName) {\n    emit([doc.queueName, doc.started], doc);\n  }\n};"
    }
  },
  "filters": {
    "byQueue": "function(doc,req){\n\treturn doc.queueName===req.query.queue;\n};",
    "byJob": "function(doc,req){\n\treturn doc.type==='job' && doc._id===req.query.job;\n};",
    "signUp": "function(doc,req){\n\treturn doc.type==='signup';\n};",
    "checkIn": "function(doc,req){\n\treturn doc.type==='checkin';\n};"
  }
}