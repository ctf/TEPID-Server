"""Migrations applicable to all types"""

from migration import Migration
from migration_setup import *
from utils import replace_nothing_with_value

def migrate_add_schema(doc:str):
    replace_nothing_with_value(doc, "_schema", "00-00-00")

migration_add_schema = Migration(
    None,
    None,
    migrate_add_schema,
    "00-00-00"
)