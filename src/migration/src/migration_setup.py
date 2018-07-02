import os
import re

from cloudant.client import CouchDB
from cloudant.result import Result, ResultByKey
from cloudant.design_document import DesignDocument

conf_dir = os.environ["tepid_config_dir"] + "\\"

class DbConfig(object):
    def __init__(self, dbUsername, dbPassword, dbUrl):
        self.dbUsername = dbUsername
        self.dbPassword = dbPassword
        urlSplit = re.search('.*:\d+/', dbUrl).end()
        self.dbUrlBase = dbUrl[:urlSplit]
        self.dbUrlDb = dbUrl[urlSplit:]


def parseConfig(filePath):
    with open(filePath) as configFile:
        keys = {}
        for line in configFile:
            if "=" in line:
                k, v = line.split("=", 1)
                keys[k.strip()] = v.strip()

        return DbConfig(keys["COUCHDB_USERNAME"], keys["COUCHDB_PASSWORD"], keys["COUCHDB_URL"])

print (os.getcwd())
Config = parseConfig(conf_dir + "DB.properties")
client = CouchDB(user=Config.dbUsername,
                 auth_token=Config.dbPassword,
                 url=Config.dbUrlBase,
                 admin_party=False,
                 auto_renew=True)
client.connect()
session = client.session()
db = client[Config.dbUrlDb]
ddoc = DesignDocument(db, 'migrate')
ddoc.create()
