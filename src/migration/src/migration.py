from typing import List, Callable


class Migration (object):
    def __init__(self, applicable_types: List[str], applicable_schema_versions: List[str], migration_function: Callable[[str], None]):
        self.applicable_types = applicable_types
        self.applicable_schema_versions = applicable_schema_versions
        self.migration_function = migration_function

    def make(self, doc:str):
        self.migration_function(doc)


def validate_type_applicable(doc_type: str, types: List[str]):
    return doc_type in types


def validate_version_applicable(doc_schema_version: str , versions: List[str]):
    return doc_schema_version in versions


def validate_migration_applicability(doc, types: List[str], versions: List[str]):
    if not validate_type_applicable(doc.type, types):
        raise TypeError("document is of incorrect type")
    if not validate_version_applicable(doc._schema, versions):
        raise TypeError("document schema is not applicable")