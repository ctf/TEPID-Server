from migration import Migration
from migration_setup import *
from utils import getQuotedOrNull

def add_sql_jobs_migration_views(ddoc):
	ddoc.add_view('migrate_jobs_00_01_00_to_sql', """function (doc){
	if (doc.type==="job") {emit(doc._id);}
	 }"""
				  )
	ddoc.save()

def migrate_jobs_to_sql(doc:dict):
	sql = f"""insert into printjob(_id, "type", colorpages, deletedataon, destination, error, eta, failed, file, isrefunded, "name", originalhost, pages, printed, processed, queuename, received, started, useridentification ) values({getQuotedOrNull(doc, '_id')}, {getQuotedOrNull(doc,'type')}, {getQuotedOrNull(doc,'colorPages')}, {getQuotedOrNull(doc,'deleteDataOn')}, {getQuotedOrNull(doc,'destination')}, {getQuotedOrNull(doc,'error')}, {getQuotedOrNull(doc,'eta')}, {getQuotedOrNull(doc,'failed')}, {getQuotedOrNull(doc,'file')}, {doc.get('refunded', False)}, {getQuotedOrNull(doc,'name')}, {getQuotedOrNull(doc,'originalHost')}, {getQuotedOrNull(doc,'pages')}, {getQuotedOrNull(doc,'printed')}, {getQuotedOrNull(doc,'processed')}, {getQuotedOrNull(doc,'queueName')}, {getQuotedOrNull(doc,'received')}, {getQuotedOrNull(doc,'started')}, {getQuotedOrNull(doc,'userIdentification')}) ON CONFLICT DO NOTHING;"""

	cur = con.cursor()
	cur.execute(sql)
	con.commit()
	cur.close()

migration_users_to_sql = Migration(
	['job'],
	None,
	migrate_jobs_to_sql,
	['00-01-00'],
	db,
	ddoc)
