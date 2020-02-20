import base64
import json
from contextlib import ExitStack
from typing import List

import javaproperties
import ldap
import requests


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
	raw_results = l.search_s(
		base=props['LDAP']['LDAP_SEARCH_BASE'],
		scope=ldap.SCOPE_SUBTREE,
		filterstr=f'(&(objectClass=user)(|{quota_groups_filter}))',
		attrlist=["sAMAccountName"]
		)

	results = [r[1].get('sAMAccountName')[0].decode('utf-8') for r in raw_results[:-1]]
	return results

def authenticate(props):
	URL = f"{props['URL']['SERVER_URL_PRODUCTION']}sessions/"

	data = {
		'username':props['LDAPResource']['LDAP_RESOURCE_USER'],
		'password':props['LDAPResource']['LDAP_RESOURCE_CREDENTIALS']
		}
	headers = {'content-type': 'application/json'}

	r = requests.post(url=URL, data=json.dumps(data), headers=headers)

	return json.loads(r.content)


def make_auth_headers(session):
	token = str(base64.b64encode(f"{session['user']['shortUser']}:{session['_id']}".encode("utf-8")), "utf-8")
	return {'Authorization': f"Token {token}"}


def get_user_semesters(props, shortUser, session):
	URL = f"{props['URL']['SERVER_URL_PRODUCTION']}users/{shortUser}/semesters?queryfor=enrolled"

	headers = make_auth_headers(session)

	r = requests.get(URL, headers=headers)
	return json.loads(r.content)


def grant_user_semesters(props, shortUser, semesters, session):

	URL = f"{props['URL']['SERVER_URL_PRODUCTION']}users/{shortUser}/semesters"

	headers = make_auth_headers(session)
	headers['content-type'] =  'application/json'

	for semester in semesters:
		r = requests.post(url=URL, data=json.dumps(semester), headers=headers)


if __name__ == '__main__':

	props_files = ['LDAP', 'LDAPResource', 'LDAPGroups', 'URL']

	with ExitStack() as ctx:
		props = {
			fname: javaproperties.load(ctx.enter_context(open(f'/etc/tepid/{fname}.properties')))
			for fname in props_files
			}

		results = get_eligible_users_from_ldap(props)

		session = authenticate(props)

		for shortUser in results:
			semesters = get_user_semesters(props, shortUser, session)
			grant_user_semesters(props, shortUser, semesters, session)

