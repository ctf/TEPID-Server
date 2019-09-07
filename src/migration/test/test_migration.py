"""Tests for the Migration class"""
import copy
import unittest
from uuid import uuid1

import migrations.jobs
from cloudant.design_document import DesignDocument
from migration import validate_migration_applicability
from migration_setup import client

from test.utils import document_from_json_file, project_root, compare_document_content


class Tests_Validate_Applicability(unittest.TestCase):
	"""Tests for the validators"""

	ts = ["t0", "t1"]
	vs = ["v0", "v1"]
	# invalid items
	invalid_t = "t2"
	invalid_v = "v2"

	def helper(self, doc_t, doc_v, types, versions):
		doc = {"type": doc_t, "schema": doc_v}
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


class Test_Migration(unittest.TestCase):
	db_name = None
	test_db = None

	def setUp(self):
		self.db_name = 'tepid_test' + str(uuid1())
		self.test_db = client.create_database(self.db_name)
		self.ddoc = DesignDocument(self.test_db, "migrate")
		self.ddoc.add_view("testViewReturnsValid", """function (doc){
  if((doc.type==="job") && (doc.schema=="00-00-00"))
  {emit(doc._id);}
}""")
		self.ddoc.add_view("testViewReturnsInvalid", """function (doc){
  if((doc.type==="job") && (doc.schema=="00-01-00"))
  {emit(doc._id);}
}""")
		self.ddoc.save()

		self.job_no_schema = document_from_json_file(self.test_db, project_root + "/test/resources/job_no_schema.json")
		self.job_no_schema["_id"] = "0"
		self.job_00_00_00 = document_from_json_file(self.test_db, project_root + "/test/resources/job_with_schema.json")
		self.job_00_00_00["_id"] = "1"
		self.job_00_01_00 = document_from_json_file(self.test_db, project_root + "/test/resources/job_00_01_00.json")
		self.job_00_01_00["_id"] = "2"
		self.job_no_schema.save()
		self.job_00_00_00.save()
		self.job_00_01_00.save()

	def tearDown(self):
		client.delete_database(self.db_name)

	def test_Apply_On_View_Valid(self):
		migration = migrations.jobs.migration_jobs_00_00_00_to_00_01_00
		migration.db = self.test_db
		migration.ddoc = self.ddoc
		migration.apply_on_view("testViewReturnsValid")

		# print("1" + self.test_db[self.job_00_00_00["_id"]])
		# print("2" + self.test_db[self.job_00_01_00["_id"]])

		self.assertTrue(
			compare_document_content(self.test_db[self.job_00_00_00["_id"]], self.test_db[self.job_00_01_00["_id"]]))

	def test_Apply_On_View_Invalid(self):
		job_00_00_00_static = copy.deepcopy(self.job_00_00_00)
		migration = migrations.jobs.migration_jobs_00_00_00_to_00_01_00
		migration.db = self.test_db
		migration.ddoc = self.ddoc
		migration.apply_on_view("testViewReturnsInvalid")

		self.assertTrue(compare_document_content(self.test_db[self.job_00_00_00["_id"]], job_00_00_00_static))


if __name__ == '__main__':
	unittest.main(verbosity=2)
