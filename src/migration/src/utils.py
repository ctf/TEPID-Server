def replace_null_with_value(dict, property, value):
    if dict[property] == "null":
        dict[property] = value


def replace_nothing_with_value(dict, property, value):
    if property not in dict:
        dict[property] = value

def update_schema_version(doc, version):
    doc._schema = version