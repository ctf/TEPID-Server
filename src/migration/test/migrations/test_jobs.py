"""Tests for the migrations for Jobs objects"""
import copy
import unittest

from migrations.jobs import migration_jobs_00_00_00_to_00_01_00

from test.utils import document_from_json_file, project_root


class Tests_00_00_00_to_00_01_00(unittest.TestCase):

	def setUp(self):
		self.job_00_00_00 = document_from_json_file(project_root + "/test/resources/job_with_schema.json")
		self.job_00_01_00 = document_from_json_file(project_root + "/test/resources/job_00_01_00.json")

	def test_all(self):
		actual = copy.deepcopy(self.job_00_00_00)
		migration_jobs_00_00_00_to_00_01_00.make(actual)
		expected = self.job_00_01_00
		self.assertEqual(expected, actual)


if __name__ == '__main__':
	unittest.main(verbosity=2)
