def replace_null_with_value(dict, property, value):
	if dict[property] == "null":
		dict[property] = value


def replace_nothing_with_value(dict, property, value):
	if property not in dict:
		dict[property] = value


def update_schema_version(doc, version):
	if version is not None:
		doc["schema"] = version


def getQuotedOrNull(doc, key, max_chars=255):
	v = doc.get(key, None)
	if v is None:
		return 'NULL'
	v = str(v).translate({ord(i): i*2 for i in '\'\"`'})[:max_chars]
	return f"'{v}'"
