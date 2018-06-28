"""Tests for the migrations for Jobs objects"""

from migrations.jobs import makeMigrationJobs00_00_00_to_00_01_00

import unittest

class Tests_00_00_00_to_00_01_00(unittest.TestCase):

    def test_no_printed(self):
        makeMigrationJobs00_00_00_to_00_01_00()
