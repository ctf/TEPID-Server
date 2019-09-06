from migration import Migration
from migration_setup import *
from utils import getQuotedOrNull


def add_sql_users_migration_views(ddoc):
	ddoc.add_view('migrate_user_00_01_00_to_sql', """function (doc){
	if ((doc.type==="user") && (doc.schema=="00-01-00")) {emit(doc._id);}
	 }"""
				  )
	ddoc.save()


def migrate_users_to_sql(doc: dict):
	o = doc

	if doc.get('role', None) is None:
		return

	sql = f"""insert into fulluser(_id, activesince, authtype, colorprinting, displayname, email, faculty, givenname, jobexpiration, lastname, longuser, middlename, nick, password, preferredname, realname, role, salutation, shortuser, studentid) values ('{o['_id']}','{o.get('activeSince', -1)}', {getQuotedOrNull(doc, 'authType')}, '{o['colorPrinting']}', '{o['displayName']}', '{o['email']}', {getQuotedOrNull(doc, 'faculty')}, '{o['givenName']}', '{o['jobExpiration']}', '{o['lastName']}', '{o['longUser']}', {getQuotedOrNull(doc, 'middleName')}, {getQuotedOrNull(doc, 'nick')}, {getQuotedOrNull(doc, 'password')},NULL, {getQuotedOrNull(doc, 'realName')}, {getQuotedOrNull(doc, 'role')}, {getQuotedOrNull(doc, 'salutation')}, {getQuotedOrNull(doc, 'shortUser')}, '{o.get('studentId', -1)}') ON CONFLICT DO NOTHING;"""

	print(">>>>>>>", sql)

	cur = con.cursor()
	cur.execute(sql)
	con.commit()
	cur.close()


migration_users_to_sql = Migration(
	['user'],
	None,
	migrate_users_to_sql,
	['00-01-00'],
	db,
	ddoc)
