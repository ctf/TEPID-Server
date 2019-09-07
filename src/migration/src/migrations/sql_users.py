from migration import Migration
from migration_setup import *
from utils import getQuotedOrNull


def add_sql_users_migration_views(ddoc):
	ddoc.add_view('migrate_user_00_01_00_to_sql', """function (doc){
	if ((doc.type==="user") && (doc.role)) {emit(doc._id);}
	 }"""
				  )
	ddoc.save()


def migrate_users_to_sql(doc: dict):
	o = doc
	if doc.get('role', None) is None:
		print("no role, not migrating")
		return
	sql = f"""insert into fulluser(_id, activesince, authtype, colorprinting, displayname, email, faculty, givenname, jobexpiration, lastname, longuser, middlename, nick, password, preferredname, realname, role, salutation, shortuser, studentid) values ({getQuotedOrNull(doc,'_id')},'{doc.get('activeSince', -1)}', {getQuotedOrNull(doc, 'authType')}, {getQuotedOrNull(doc, 'colorPrinting')}, {getQuotedOrNull(doc,'displayName')}, {getQuotedOrNull(doc,'email')}, {getQuotedOrNull(doc, 'faculty')}, {getQuotedOrNull(doc, 'givenName')}, {getQuotedOrNull(doc, 'jobExpiration')}, {getQuotedOrNull(doc,'lastName')}, {getQuotedOrNull(doc, 'longUser')}, {getQuotedOrNull(doc, 'middleName')}, {getQuotedOrNull(doc, 'nick')}, {getQuotedOrNull(doc, 'password')},NULL, {getQuotedOrNull(doc, 'realName')}, {getQuotedOrNull(doc, 'role')}, {getQuotedOrNull(doc, 'salutation')}, {getQuotedOrNull(doc, 'shortUser')}, '{o.get('studentId', -1)}') ON CONFLICT DO NOTHING;"""
	cur = con.cursor()
	try:
		cur.execute(sql)
		con.commit()
	except Exception as e:
		con.rollback()
		cur.close()
		raise e
	cur.close()


migration_users_to_sql = Migration(
	['user'],
	None,
	migrate_users_to_sql,
	['00-01-01'],
	db,
	ddoc)
