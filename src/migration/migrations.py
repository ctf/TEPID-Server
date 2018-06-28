from cloudant.client import CouchDB
from cloudant.result import Result, ResultByKey
from cloudant.design_document import DesignDocument


class DbConfig(object):
    def __init__(self, dbUsername, dbPassword, dbUrl):
        self.dbUsername = dbUsername
        self.dbPassword = dbPassword
        self.dbUrl = dbUrl


def parseConfig(filePath):
    with open(filePath) as configFile:
        keys = {}
        for line in configFile:
            if "=" in line:
                k, v = line.split("=", 1)
                keys[k.strip()] = v.strip()
        return DbConfig(keys["COUCHDB_USERNAME"], keys["COUCHDB_PASSWORD"], keys["COUCHDB_URL"])


Config = parseConfig("../../config/DB.properties")
client = CouchDB(user=Config.dbUsername,
                 auth_token=Config.dbPassword,
                 url=Config.dbUrl,
                 admin_party=False,
                 auto_renew=True)
client.connect()
session = client.session()
db = client['tepid-clone']
ddoc = DesignDocument(db, 'migrate')
ddoc.create()



def makeMigrationJobs00_00_00_to_00_01_00():
    ddoc.add_view('migrate_user_00_00_00_to_00_01_00', """function (doc){
  if((doc.type==="job") && (!(doc._schema) || doc._schema=="00-00-00"))
  {emit(doc._id);}
}""")
    ddoc.save()
    q = ddoc.get_view("migrate_user_00_00_00_to_00_01_00")
    results = q()

    for row in results:
        doc = db[row.key]
        try:
            # type asserts
            if not ((not doc._schema) or doc._schema=="00-00-00"):
                raise TypeError("document schema is not applicable")
            if not (doc.type == "user"):
                raise TypeError("document is of incorrect type")
            # migration


        except TypeError as e:
            print ("migration of " + doc._id + " was aborted bue to type not matching the specification for this migration: " +  e)


def replace_null_with_value(dict, property, value):
    if dict[property] == "null":
        dict[property] = value

def replace_nothing_with_value(dict, property, value):
    if property not in dict:
        dict[property] = value