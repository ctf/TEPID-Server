"""Tests for the Migration class"""

import unittest

from migration import validate_migration_applicability


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


if __name__ == '__main__':
    unittest.main(verbosity=2)
