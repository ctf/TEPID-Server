from typing import List, Callable

from migration_setup import ddoc, db
from utils import update_schema_version


class Migration (object):
    def __init__(self, applicable_types: List[str], applicable_schema_versions: List[str], migration_function: Callable[[str], None], schema_version: str):
        self.applicable_types = applicable_types
        self.applicable_schema_versions = applicable_schema_versions
        self.migration_function = migration_function
        self.schema_version = schema_version

    def make(self, doc: str):
        self.migration_function(doc)

    def apply_on_view(self, view):
        to_migrate = ddoc.get_view(view)()
        for row in to_migrate:
            doc = db[row.key]
            try:
                validate_migration_applicability(doc, self.applicable_types, self.applicable_schema_versions)
                self.migration_function(doc)
                update_schema_version(doc, self.schema_version)
                doc.save()
            except TypeError as e:
                print ("migration of " + doc._id + " was aborted due to type not matching the specification for this migration: " +  e)


def validate_type_applicable(doc_type: str, types: List[str]):
    return doc_type in types


def validate_version_applicable(doc_schema_version: str, versions: List[str]):
    return doc_schema_version in versions


def validate_migration_applicability(doc, types: List[str], versions: List[str]):
    if (types is not None) and (not validate_type_applicable(doc.type, types)):
        raise TypeError("document is of incorrect type")
    if (versions is not None) and (not validate_version_applicable(doc._schema, versions)):
        raise TypeError("document schema is not applicable")