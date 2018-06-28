from typing import List, Callable


class Migration (object):
    def __init__(self, applicable_types: List[str], applicable_schema_versions: List[str], migration_function: Callable[[str], None]):
        self.applicable_types = applicable_types
        self.applicable_schema_versions = applicable_schema_versions
        self.migration_function = migration_function

    def make(self, doc:str):
        self.migration_function(doc)