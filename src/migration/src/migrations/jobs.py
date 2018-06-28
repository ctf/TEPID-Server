from typing import List

from migration_setup import *
from utils import replace_null_with_value, replace_nothing_with_value


def add_job_migration_views():
    ddoc.add_view('migrate_user_00_00_00_to_00_01_00', """function (doc){
  if((doc.type==="job") && (!(doc._schema) || doc._schema=="00-00-00"))
  {emit(doc._id);}
}""")
    ddoc.save()


def makeMigrationJobs00_00_00_to_00_01_00():
    to_migrate = ddoc.get_view("migrate_user_00_00_00_to_00_01_00")()

    for row in to_migrate:
        doc = db[row.key]
        try:
            # type asserts

            validate_migration_applicability(doc, ["job"], ["00-00-00"])
            # migration
            replace_null_with_value(doc, "started", -1)
            replace_null_with_value(doc, "processed", -1)
            replace_null_with_value(doc, "printed", -1)
            replace_nothing_with_value(doc, "started", -1)
            replace_nothing_with_value(doc, "processed", -1)
            replace_nothing_with_value(doc, "printed", -1)
            # schema correction

            update_schema_version(doc, "00-01-00")
            doc.save()

        except TypeError as e:
            print ("migration of " + doc._id + " was aborted bue to type not matching the specification for this migration: " +  e)

def update_schema_version(doc, version):
    doc._schema = version


def validate_type_applicable(doc_type: str, types: List[str]):
    return doc_type in types


def validate_version_applicable(doc_schema_version: str , versions: List[str]):
    return doc_schema_version in versions


def validate_migration_applicability(doc, types: List[str], versions: List[str]):
    if not validate_type_applicable(doc.type, types):
        raise TypeError("document is of incorrect type")
    if not validate_version_applicable(doc._schema, versions):
        raise TypeError("document schema is not applicable")
