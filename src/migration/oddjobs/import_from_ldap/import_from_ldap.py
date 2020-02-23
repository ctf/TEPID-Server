import base64
import json
from contextlib import ExitStack
from typing import List

import javaproperties
import ldap
import requests
import logging

from tqdm import tqdm
from src.migration.oddjobs.tools import ldap_utils


def get_eligible_users_from_ldap(props) -> List[str]:
	l = ldap.initialize(props['LDAP']['PROVIDER_URL'])
	l.protocol_version = ldap.VERSION3
	l.set_option(ldap.OPT_REFERRALS, 0)
	l.set_option(ldap.OPT_NETWORK_TIMEOUT, 200000)
	l.set_option(ldap.OPT_TIMEOUT, 200000)

	unparsed_groups = map(lambda s: s.split('+'), [
		props['LDAPGroups']['QUOTA_GROUPS'],
		props['LDAPGroups']['CTFERS_GROUPS'],
		props['LDAPGroups']['ELDERS_GROUPS'],
		])
	quota_groups = [item for sublist in unparsed_groups for item in sublist]

	quota_groups_filter = ''.join(
		map(lambda g: f"(memberOf:1.2.840.113556.1.4.1941:=cn={g},{props['LDAPGroups']['GROUPS_LOCATION']})",
			quota_groups))

	logging.info('binding to LDAP')
	l.bind_s(
		props['LDAP']['SECURITY_PRINCIPAL_PREFIX'] + props['LDAPResource']['LDAP_RESOURCE_USER'],
		props['LDAPResource']['LDAP_RESOURCE_CREDENTIALS']
		)

	def query_function(pagecontrol):
		return l.search_ext(
			base=props['LDAP']['LDAP_SEARCH_BASE'],
			scope=ldap.SCOPE_SUBTREE,
			filterstr=f'(&(objectClass=user)(|{quota_groups_filter}))',
			attrlist=["sAMAccountName"],
			serverctrls=[pagecontrol]
			)

	logging.info('executing query')
	raw_results = ldap_utils.paged_search(l, query_function)

	logging.info('parsing results')
	results = [r[1].get('sAMAccountName')[0].decode('utf-8') for r in raw_results if r[0] is not None]
	return results


def authenticate(props):
	URL = f"{props['URL']['SERVER_URL_PRODUCTION']}sessions/"

	data = {
		'username': props['LDAPResource']['LDAP_RESOURCE_USER'],
		'password': props['LDAPResource']['LDAP_RESOURCE_CREDENTIALS']
		}
	headers = {'content-type': 'application/json'}

	r = requests.post(url=URL, data=json.dumps(data), headers=headers)

	return json.loads(r.content)


def make_auth_headers(session):
	token = str(base64.b64encode(f"{session['user']['shortUser']}:{session['_id']}".encode("utf-8")), "utf-8")
	return {'Authorization': f"Token {token}"}


def get_user(props, userID, session):
	URL = f"{props['URL']['SERVER_URL_PRODUCTION']}users/{userID}"

	headers = make_auth_headers(session)

	r = requests.get(URL, headers=headers)

	return json.loads(r.content)


def get_user_semesters(props, shortUser, session):
	URL = f"{props['URL']['SERVER_URL_PRODUCTION']}users/{shortUser}/semesters?queryfor=enrolled"

	headers = make_auth_headers(session)

	r = requests.get(URL, headers=headers)

	return json.loads(r.content)


def grant_user_semesters(props, shortUser, semesters, session):
	URL = f"{props['URL']['SERVER_URL_PRODUCTION']}users/{shortUser}/semesters"

	headers = make_auth_headers(session)
	headers['content-type'] = 'application/json'

	for semester in semesters:
		r = requests.post(url=URL, data=json.dumps(semester), headers=headers)


def migrate():
	try:
		try:
			semesters = get_user_semesters(props, shortUser, session)
		except:
			get_user(props, shortUser, session)
			semesters = get_user_semesters(props, shortUser, session)

		grant_user_semesters(props, shortUser, semesters, session)
	except:
		logging.error(f'failed processing for user {shortUser}')


if __name__ == '__main__':

	props_files = ['LDAP', 'LDAPResource', 'LDAPGroups', 'URL']

	with ExitStack() as ctx:
		props = {
			fname: javaproperties.load(ctx.enter_context(open(f'/etc/tepid/{fname}.properties')))
			for fname in props_files
			}

		logging.info('begin ldap query')
		results = get_eligible_users_from_ldap(props)

		logging.info('authenticate with tepid')
		session = authenticate(props)

		logging.info('add semesters')
		for shortUser in tqdm(results):
			migrate()