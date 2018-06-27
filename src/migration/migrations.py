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
