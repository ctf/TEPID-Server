import json
import os

from cloudant.document import Document

project_root = os.environ['PYTHONPATH'].split(os.pathsep)[0]


def document_from_json_file(db, file_path):
    with open(file_path) as blob:
        json_obj = json.loads(blob.read())
        doc = Document(db, json_obj["_id"])
        doc.update(json_obj)
        return doc


def compare_document_content(this, that):
    meta = ["_id", "_rev"]
    this_filtered = dict((k, v) for k, v in this.items() if k not in meta)
    that_filtered = dict((k, v) for k, v in that.items() if k not in meta)
    return this_filtered == that_filtered
