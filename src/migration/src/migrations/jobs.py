from migration import Migration
from migration_setup import *
from utils import replace_nothing_with_value


def add_job_migration_views():
	ddoc.add_view('migrate_user_00_00_00_to_00_01_00', """function (doc){
  if((doc.type==="job") && (doc.schema=="00-00-00"))
  {emit(doc._id);}
}""")
	ddoc.save()


def migrate_00_00_00_to_00_01_00(doc: str):
	replace_nothing_with_value(doc, "processed", -1)
	replace_nothing_with_value(doc, "printed", -1)
	replace_nothing_with_value(doc, "failed", -1)
	replace_nothing_with_value(doc, "received", -1)


migration_jobs_00_00_00_to_00_01_00 = Migration(
	["job"],
	["00-00-00"],
	migrate_00_00_00_to_00_01_00,
	"00-01-00")
