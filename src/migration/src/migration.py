from typing import List, Callable, Optional

import migration_setup
from utils import update_schema_version


class Migration (object):
    def __init__(self, applicable_types: Optional[List[str]], applicable_schema_versions: Optional[List[str]], migration_function: Callable[[str], None], schema_version: str, db=migration_setup.db, ddoc=migration_setup.ddoc):
        self.applicable_types = applicable_types
        self.applicable_schema_versions = applicable_schema_versions
        self.migration_function = migration_function
        self.schema_version = schema_version
        self.db = db
        self.ddoc = ddoc
        self.ddoc.fetch()

    def make(self, doc: str):
        self.migration_function(doc)
        update_schema_version(doc, self.schema_version)

    def apply_on_view(self, view_name):
        view = self.ddoc.get_view(view_name)()
        total_to_migrate = view['total_rows']
        docs_migrated = 0
        skip = 0
        while (len(self.ddoc.get_view(view_name)(skip=skip, limit=1)['rows']) > 0 ):
            view = self.ddoc.get_view(view_name)(include_docs=True, limit=1000, skip=skip)
            to_migrate = view['rows']
            for row in to_migrate:
                print("migrating {count} of {total}".format(count=docs_migrated, total=total_to_migrate))
                doc = self.db.create_document(row['doc'])
                try:
                    validate_migration_applicability(doc, self.applicable_types, self.applicable_schema_versions)
                    self.make(doc)
                    doc.save()
                    docs_migrated += 1
                except TypeError as e:
                    skip += 1
                    print("migration of " + doc['_id'] + " was aborted due to type not matching the specification for this migration: " + str(e))


def validate_type_applicable(doc_type: str, types: List[str]):
    return doc_type in types


def validate_version_applicable(doc_schema_version: str, versions: List[str]):
    return doc_schema_version in versions


def validate_migration_applicability(doc, types: List[str], versions: List[str]):
    if (types is not None) and (not validate_type_applicable(doc['type'], types)):
        raise TypeError("document is of incorrect type")
    if (versions is not None) and (not validate_version_applicable(doc['schema'], versions)):
        raise TypeError("document schema is not applicable")
    return True
