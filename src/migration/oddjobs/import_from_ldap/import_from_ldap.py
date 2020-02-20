from contextlib import ExitStack

import javaproperties

if __name__ == '__main__':

	props_files = ['LDAP', 'LDAPResource', 'LDAPGroups', 'URL']

	with ExitStack() as ctx:
		props = {
			fname: javaproperties.load(ctx.enter_context(open(f'/etc/tepid/{fname}.properties')))
			for fname in props_files
			}
