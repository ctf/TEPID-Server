from contextlib import ExitStack
from typing import List

import javaproperties
import ldap


def get_eligible_users_from_ldap(props) -> List[str]:
	l = ldap.initialize(props['LDAP']['PROVIDER_URL'])
	l.protocol_version = ldap.VERSION3
	l.set_option(ldap.OPT_REFERRALS, 0)

	unparsed_groups = map(lambda s: s.split('+'), [
		props['LDAPGroups']['QUOTA_GROUPS'],
		props['LDAPGroups']['CTFERS_GROUPS'],
		props['LDAPGroups']['ELDERS_GROUPS'],
		])
	quota_groups = [item for sublist in unparsed_groups for item in sublist]

	quota_groups_filter = ''.join(
		map(lambda g: f"(memberOf:1.2.840.113556.1.4.1941:=cn={g},{props['LDAPGroups']['GROUPS_LOCATION']})",
			quota_groups))

	l.bind_s(
		props['LDAP']['SECURITY_PRINCIPAL_PREFIX'] + props['LDAPResource']['LDAP_RESOURCE_USER'],
		props['LDAPResource']['LDAP_RESOURCE_CREDENTIALS']
		)

if __name__ == '__main__':

	props_files = ['LDAP', 'LDAPResource', 'LDAPGroups', 'URL']

	with ExitStack() as ctx:
		props = {
			fname: javaproperties.load(ctx.enter_context(open(f'/etc/tepid/{fname}.properties')))
			for fname in props_files
			}

		results = get_eligible_users_from_ldap(props)