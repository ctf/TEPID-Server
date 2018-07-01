"""Tests for the Migration class"""

import unittest
from uuid import uuid1

from migration import validate_migration_applicability
from migration_setup import client
from test.utils import document_from_json_file, project_root


class Tests_Validate_Applicability(unittest.TestCase):
    """Tests for the validators"""

    ts = ["t0", "t1"]
    vs = ["v0", "v1"]
    # invalid items
    invalid_t = "t2"
    invalid_v = "v2"

    def helper(self, doc_t, doc_v, types, versions):
        doc = {"type": doc_t, "_schema": doc_v}
        actual = validate_migration_applicability(doc, types, versions)
        self.assertEqual(True, actual)

    def test_normal(self):
        self.helper(self.ts[0], self.vs[0], self.ts, self.vs)

    def test_none_for_types(self):
        self.helper(self.ts[0], self.vs[0], None, self.vs)

    def test_none_for_versions(self):
        self.helper(self.ts[0], self.vs[0], self.ts, None)

    def test_inapplicable_type(self):
        with self.assertRaises(TypeError):
            self.helper(self.ts[0], self.vs[0], self.invalid_t, self.vs)

    def test_inapplicable_version(self):
        with self.assertRaises(TypeError):
            self.helper(self.ts[0], self.vs[0], self.ts, self.invalid_v)

class Test_Migration (unittest.TestCase):

    db_name = None
    test_db = None

    def setUp(self):
        self.db_name = 'tepid_test' + str(uuid1())
        self.test_db = client.create_database(self.db_name)
        self.job_no_schema = document_from_json_file(project_root + "/test/resources/job_no_schema.json")
        self.job_00_00_00 = document_from_json_file(project_root + "/test/resources/job_with_schema.json")
        self.job_00_01_00 = document_from_json_file(project_root + "/test/resources/job_00_01_00.json")

    def tearDown(self):
        client.delete_database(self.db_name)

    def test_Apply_On_View_Valid(self):
        self.fail()

    def test_Apply_On_View_Invalid(self):
        self.fail()

if __name__ == '__main__':
    unittest.main(verbosity=2)
