import os
import re
from collections import namedtuple

import psycopg2
from cloudant.client import CouchDB
from cloudant.design_document import DesignDocument

conf_dir = os.environ["tepid_config_dir"]

Psql = namedtuple('Psql', ['conn', 'Config'])
psql = None
Couch = namedtuple('Couch', ['client', 'db', 'session', 'ddoc', 'Config'])
couch = None


class DbConfig(object):
	def __init__(self, dbUsername, dbPassword, dbUrl):
		self.dbUsername = dbUsername
		self.dbPassword = dbPassword
		urlSplit = re.search('.*:\d+/', dbUrl).end()
		self.dbUrlBase = dbUrl[:urlSplit - 1]
		self.dbUrlDb = dbUrl[urlSplit:]
		portSeparator = self.dbUrlBase.rfind(':')
		self.host, self.port = self.dbUrlBase[:portSeparator], self.dbUrlBase[portSeparator:+1]


def parseConfig(filePath):
	with open(filePath) as configFile:
		keys = {}
		for line in configFile:
			if "=" in line:
				k, v = line.split("=", 1)
				keys[k.strip()] = v.strip()

		return DbConfig(keys["USERNAME"], keys["PASSWORD"], keys["URL"])


def loadCouchDb(Config):
	client = CouchDB(user=Config.dbUsername,
					 auth_token=Config.dbPassword,
					 url=Config.dbUrlBase,
					 admin_party=False,
					 auto_renew=True)
	client.connect()
	session = client.session()
	db = client[Config.dbUrlDb]
	ddoc = DesignDocument(db, 'migrate')
	# ddoc.create()
	return Couch(client=client, db=db, session=session, ddoc=ddoc, Config=Config)


def loadPsql(Config):
	conn = psycopg2.connect(host=Config.host, port=Config.port, database=Config.dbUrlDb, user=Config.dbUsername,
							password=Config.dbPassword)
	cur = conn.cursor()

	return Psql(conn=conn, Config=Config)
