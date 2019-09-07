import copy
import unittest

from migrations.any import migration_add_schema

from test.utils import document_from_json_file, project_root


class Tests_Migrate_Add_Schema(unittest.TestCase):

	def setUp(self):
		self.job_no_schema = document_from_json_file(project_root + "/test/resources/job_no_schema.json")
		self.job_with_schema = document_from_json_file(project_root + "/test/resources/job_with_schema.json")

	def test_add_schema(self):
		actual = copy.deepcopy(self.job_no_schema)
		migration_add_schema.make(actual)
		expected = self.job_with_schema
		self.assertEqual(expected, actual)


if __name__ == '__main__':
	unittest.main(verbosity=2)
