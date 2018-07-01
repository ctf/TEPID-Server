import json
import os

from cloudant.document import Document

from migration_setup import db

project_root = os.environ['PYTHONPATH'].split(os.pathsep)[0]

def document_from_json_file(file_path):
    with open(file_path) as blob:
        json_obj = json.loads(blob.read())
        doc = Document(db, json_obj["_id"])
        doc.update(json_obj)
        return doc
