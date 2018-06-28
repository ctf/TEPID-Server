from migration_setup import *
from utils import replace_null_with_value, replace_nothing_with_value


def makeMigrationJobs00_00_00_to_00_01_00():
    ddoc.add_view('migrate_user_00_00_00_to_00_01_00', """function (doc){
  if((doc.type==="job") && (!(doc._schema) || doc._schema=="00-00-00"))
  {emit(doc._id);}
}""")
    ddoc.save()
    q = ddoc.get_view("migrate_user_00_00_00_to_00_01_00")
    results = q()

    for row in results:
        doc = db[row.key]
        try:
            # type asserts
            if not ((not doc._schema) or doc._schema=="00-00-00"):
                raise TypeError("document schema is not applicable")
            if not (doc.type == "user"):
                raise TypeError("document is of incorrect type")
            # migration
            replace_null_with_value(doc, "started", -1)
            replace_null_with_value(doc, "processed", -1)
            replace_null_with_value(doc, "printed", -1)
            replace_nothing_with_value(doc, "started", -1)
            replace_nothing_with_value(doc, "processed", -1)
            replace_nothing_with_value(doc, "printed", -1)


        except TypeError as e:
            print ("migration of " + doc._id + " was aborted bue to type not matching the specification for this migration: " +  e)
