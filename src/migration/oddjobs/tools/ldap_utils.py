from ldap.controls import SimplePagedResultsControl


def paged_search(conn, query_function):
	page_control = SimplePagedResultsControl(True, size=1000, cookie='')

	response = query_function(page_control)

	result = []
	pages = 0
	while True:
		pages += 1
		rtype, rdata, rmsgid, serverctrls = conn.result3(response)
		result.extend(rdata)
		controls = [control for control in serverctrls
			if control.controlType == SimplePagedResultsControl.controlType]
		if not controls:
			print('The server ignores RFC 2696 control')
			break
		if not controls[0].cookie:
			break
		page_control.cookie = controls[0].cookie
		response = query_function(page_control)

	return result