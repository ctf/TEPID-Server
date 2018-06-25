from cloudant.client import CouchDB


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
client = CouchDB(Config.dbUsername, Config.dbPassword, Config.dbUrl)
session = client.session()
db = client['tepid']
