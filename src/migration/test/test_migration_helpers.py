"""Tests for the general helper utils"""

import unittest

from utils import replace_null_with_value, replace_nothing_with_value


class Tests_Replace_Null_With_Value(unittest.TestCase):
	"""Tests for replace_null_with_value"""

	p = "property"
	v = "value"
	r = "replacedValue"

	def helper(self, test_val, expected):
		u = {self.p: test_val}
		replace_null_with_value(u, self.p, self.r)
		self.assertEqual(u[self.p], expected)

	def test_null_present(self):
		"""Tests standard situation where a null is present"""
		self.helper("null", self.r)

	def test_value_present(self):
		"""Tests standard situation where a value is present"""
		self.helper(self.v, self.v)

	def test_key_missing(self):
		"""Tests the situation where the property is absent"""
		with self.assertRaises(KeyError):
			u = {self.p: self.v}
			replace_null_with_value(u, "non_existent_property", self.r)


class Tests_Replace_Nothing_With_Value(unittest.TestCase):
	"""Tests for replace_null_with_value"""

	p = "property"
	o = "otherProperty"
	v = "value"
	r = "replacedValue"

	def test_propery_present(self):
		"""Tests when the property should be left as is"""
		u = {self.p: self.v}
		replace_nothing_with_value(u, self.p, self.r)
		self.assertEqual(u[self.p], self.v)

	def test_property_absent(self):
		"""Tests when the propery is absent and the value should be used"""
		u = {self.o: self.v}
		replace_nothing_with_value(u, self.p, self.r)
		self.assertEqual(u[self.o], self.v)
		self.assertEqual(u[self.p], self.r)


if __name__ == '__main__':
	unittest.main(verbosity=2)
