angular.module('tepid')
.service('tepidServer', function($rootScope, $http, $log, $q, $timeout, $window) {
	//~ this.url = '***REMOVED***';
	this.url = 'http://localhost:8080/tepid';
	var service = this, watchers = {},
	reqs = {}, pollQueue = function(since, queue) {
		if (watchers[queue] < 1) return;
		//cancel any old requests that might be lingering
		if (since === 0 && reqs[queue]) reqs[queue].resolve();
		reqs[queue] = $q.defer();
		$http.get(service.url + '/queues/' + queue + '/_changes?feed=longpoll&since=' + since, service.getOptions().timeout(reqs[queue].promise).ignoreLoadingBar().build()).then(function(response) {
			since = response.data.last_seq;
			if (response.data.results && response.data.results.length) {
				service._notifyWatchers({changes:response.data.results, queue:queue});
			} 
			pollQueue(since, queue);
		}, function(){
			//if long-poll fails
			$timeout(function() {
				pollQueue(since, queue);
			}, 1000);
		});
	};
		
	var RequestOptions = function() {
		this.options = {};
	};
	RequestOptions.prototype.build = function() {
		return this.options;
	};
	RequestOptions.prototype.addHeader = function(k, v) {
		if (!this.options.headers) this.options.headers = {};
		this.options.headers[""+k] = ""+v;
		return this;
	};
	RequestOptions.prototype.ignoreLoadingBar = function(ignore) {
		this.options.ignoreLoadingBar = ignore !== false;
		return this;
	};
	RequestOptions.prototype.timeout = function(promise) {
		this.options.timeout = promise;
		return this;
	};
	this.getOptions = function() {
		return new RequestOptions()
		.addHeader('Authorization', 'Token ' + $window.btoa(service.getSessionUn() + ':' + service.getSessionId()));
	};
	
	this.processError = function(err) {
		if (err.status === 401) {
			service.session = null;
			$rootScope.logout();
		} else {
			$log.error(err);
		}
	}
	
	this.getSession = function() {
		if (!service.session) {
			service.session = $rootScope.session;
		}
		return service.session;
	}
	
	this.getSessionId = function() {
		var session = service.getSession();
		return session && session._id ? session._id : '';
	}
	
	this.getSessionUn = function() {
		var session = service.getSession();
		return session && session.user ? session.user.shortUser : '';
	}
	
	this.watchQueue = function(scope, queue, cb) {
		if (!watchers[queue]) {
			watchers[queue] = 1;
			pollQueue(0, queue);
		} else {
			watchers[queue]++;
		}
		var handler = $rootScope.$on('queue-change-' + queue, cb);
        if (scope) {
			scope.$on('$destroy', handler);
			scope.$on('$destroy', function() {
				service._unwatch(queue);
			});
		}
	};
	
	this._unwatch = function(queue) {
		watchers[queue]--;
		//cancel polling for queue no longer being watched
		if (watchers[queue] < 1 && reqs[queue]) reqs[queue].resolve();
	};
	
	this._notifyWatchers = function(data) {
		$rootScope.$emit('queue-change-' + data.queue, data.changes);
	};

	this.listQueues = function() {
		return $http.get(service.url + "/queues", service.getOptions().build()).then(function(response) {
			if (response.data && response.data.length && response.data[0].name) {
				//used by nav to link to first queue
				$rootScope.firstQ = response.data[0].name;
			}
			return response.data;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};

	this.watchQueues = function(scope, cb) {
		var since = -1, watching = true;
		var pollChanges = function() {
			if (!watching) return;
			$http.get(service.url + "/queues/_changes?since="+since, service.getOptions().ignoreLoadingBar().build()).then(function(response) {
				if (response.status === 200 && response.data) {
					if (response.data.results) cb(response.data.results);
					since = response.data.last_seq;
				}
				pollChanges();
			}, function() {
				$timeout(pollChanges, 5000);
			});
		};
	    if (scope) {
			scope.$on('$destroy', function() {
				watching = false;
			});
		}
	};
	
	this.saveQueues = function(queues) {
		return $http.put(service.url + "/queues", queues, service.getOptions().build());
	};
	
	this.deleteQueue = function(id) {
		return $http.delete(service.url + "/queues/" + id, service.getOptions().build());
	};
	
	this.deleteDestination = function(id) {
		return $http.delete(service.url + "/destinations/" + id, service.getOptions().build());
	};

	this.listDestinations = function() {
		return $http.get(service.url + "/destinations", service.getOptions().build()).then(function(response) {
			return response.data;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};
	
	this.saveDestinations = function(destinations) {
		return $http.put(service.url + "/destinations", destinations, service.getOptions().build()).catch(function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};
	
	this.setDestinationUp = function(id, up, reason) {
		if (!up && !reason) return;
		return $http.post(service.url + "/destinations/" + id, {up: up, reason:reason}, service.getOptions().build()).catch(function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};
	
	var getPages = function(job, max, queue) {
		if (!job.processed) return null;
		if (!max) max = job.pages;
		var out = [];
		for (var p = 0; p < job.pages && p < max; p++) out.push({
			i: p,
			src: service.url + "/queues/" + queue + "/" + job._id + "/" + p + ".png",
			href: service.url + "/queues/" + queue + "/" + job._id + "/pdf"
		});
		return out;
	};
	
	this.getUser = function(user) {
		return $http.get(service.url + "/users/" + user + '?noRedirect', service.getOptions().build()).then(function(response) {
			return response.data;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};
	
	this.setExchange = function(user, exchange) {
		return $http.put(service.url + "/users/" + user + '/exchange', exchange, service.getOptions().build()).then(function(response) {
			return response.data;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};
	
	this.setNick = function(user, nick) {
		return $http.put(service.url + "/users/" + user + '/nick', nick, service.getOptions().ignoreLoadingBar().build()).then(function(response) {
			return response.data;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};
	
	this.setColorPrinting = function(user, color) {
		return $http.put(service.url + "/users/" + user + '/color', color, service.getOptions().build()).then(function(response) {
			return response.data;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};
	
	this.authenticate = function(username, password, persistent) {
		var deferred = $q.defer();
		$http.post(service.url + "/sessions/", {username:username,password:password,persistent:persistent}).then(function(response) {
			if (response.status === 200 && response.data && response.data._id) {
				$rootScope.session = response.data;
				service.session = response.data;
				deferred.resolve(response.data);
			} else {
				deferred.reject("Authentication failed");
				service.session = null;
			}
		}, function() {
			deferred.reject("Authentication failed");
			service.session = null;
		});
		return deferred.promise;
	};
	
	this.checkSession = function(username, token) {
		return $http.get(service.url + "/sessions/" + username + "/" + token).then(function(response) {
			return response.status === 200 && !!response.data;
		}, function() {
			return false;
		});
	};
	this.refreshSession = function(username, token) {
		return $http.get(service.url + "/sessions/" + username + "/" + token).then(function(response) {
			if (response.status === 200 && response.data && response.data._id) {
				$rootScope.session = response.data;
				service.session = response.data;
				return response.data;
			}
		});
	};
	
	this.logout = function() {
		if (service.session) return $http.delete(service.url + "/sessions/" + service.session._id, service.getOptions().build()).then(function(out) {
			service.session = null;
			return out;
		});
	};
	
	this.getUserAutoSuggest = function(user) {
		return $http.get(service.url + "/users/autosuggest/" + user + "?limit=15", service.getOptions().ignoreLoadingBar().build()).then(function(response) {
			for (var i = 0; i < response.data.length; i++) {
				var user = response.data[i];
				user.firstLast = (user.longUser||"").split('@')[0];
			}
			return response.data;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};
	
	this.getBarcode = function() {
		var deferred = $q.defer();
		return {
			cancel: deferred, 
			req: $http.get(service.url + "/barcode/_wait", service.getOptions().timeout(deferred.promise).ignoreLoadingBar().build()).then(function(response) {
				return response.data;
			}, function(err) {
				service.processError(err);
				return $q(function(resolve,reject){reject(err)});
			})
		};
	};
	
	this.getJobsByUser = function(user) {
		return $http.get(service.url + "/jobs/" + user, service.getOptions().build()).then(function(response) {
			var jobs = [];
			for (var i = 0; i < response.data.length; i++) {
				var j = response.data[i];
				jobs[i] = {
					position: i,
					queue: j.queueName,
					name: j.name,
					pages: j.pages || '',
					status: j.refunded ? 'Refunded' : (j.failed ? 'Failed' : (j.printed ? 'Printed' : ('Process' + (j.processed ? 'ed' : 'ing')))),
					started: moment(j.started).fromNow(),
					preview: getPages(j, 5, j.queueName),
					host: j.originalHost,
					allData: j
				}
				if (j.colorPages) jobs[i].pages += ' (' + j.colorPages + ' color)';
			}
			return jobs;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};
	
	this.getJobs = function(queue, limit) {
		return $http.get(service.url + "/queues/" + queue + (limit?'?limit='+limit:''), service.getOptions().build()).then(function(response) {
			var jobs = [];
			for (var i = 0; i < response.data.length; i++) {
				var j = response.data[i];
				jobs[i] = {
					position: i,
					user: j.userIdentification,
					name: j.name,
					pages: j.pages || '',
					status: j.refunded ? 'Refunded' : (j.failed ? 'Failed' : (j.printed ? 'Printed' : ('Process' + (j.processed ? 'ed' : 'ing')))),
					started: moment(j.started).fromNow(),
					preview: getPages(j, 5, queue),
					host: j.originalHost,
					allData: j
				}
				if (j.colorPages) jobs[i].pages += ' (' + j.colorPages + ' color)';
			}
			return jobs;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};
	
	this.setJobRefunded = function(id, refunded) {
		return $http.put(service.url + "/jobs/job/" + id + '/refunded', refunded, service.getOptions().build()).then(function(response) {
			return response.data;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};
	
	this.reprintJob = function(id) {
		return $http.post(service.url + "/jobs/job/" + id + '/reprint', null, service.getOptions().build()).then(function(response) {
			return response.data;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};
	
	this.setJobExpiration = function(sam, expiration) {
		return $http.put(service.url + "/users/" + sam + '/jobExpiration', expiration, service.getOptions().build()).then(function(response) {
			return response.data;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};
	
	this.getQuota = function(shortUser) {
		return $http.get(service.url + "/users/" + shortUser + "/quota", service.getOptions().build()).then(function(response) {
			return response.data;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};
	
	this.getAdminConfigured = function() {
		return $http.get(service.url + "/users/configured", service.getOptions().ignoreLoadingBar().build()).then(function(response) {
			return response.data;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};
	
	this.createLocalAdmin = function(newAdmin) {
		return $http.put(service.url + "/users/" + newAdmin.shortUser, newAdmin, service.getOptions().build()).catch(function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};

	this.getSignUp = function(shortUser) {
		return $http.get(service.url + "/office-hours/" + shortUser, service.getOptions().build()).then(function(response) {
			return response.data;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};

	this.getSignUps = function() {
		return $http.get(service.url + "/office-hours/", service.getOptions().build()).then(function(response) {
			return response.data;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};

	this.setSignUp = function(name, givenName, userOH) {
		return $http.post(service.url + "/office-hours/" + name + "/" + givenName, userOH, service.getOptions().build()).then(function(response) {
			return response.data;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};

	this.watchSignUp = function(scope, cb) {
		var since = -1, watching = true;
		var pollChanges = function() {
			if (!watching) return;
			$http.get(service.url + "/office-hours/_changes?feed=longpoll&since="+since, service.getOptions().ignoreLoadingBar().build()).then(function(response) {
				if (response.status === 200 && response.data) {
					if (response.data.results) cb(response.data.results);
					since = response.data.last_seq;
				}
				pollChanges();
			}, function() {
				$timeout(pollChanges, 5000);
			});
		};
		pollChanges();
		if (scope) {
			scope.$on('$destroy', function() {
				watching = false;
			});
		}
	};

	this.getCheckedInUsers = function() {
		return $http.get(service.url + "/check-in/", service.getOptions().build()).then(function(response) {
			return response.data;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};

	this.getUserOH = function(shortUser) {
		return $http.get(service.url + "/check-in/" + shortUser, service.getOptions().build()).then(function(response) {
			return response.data;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};

	this.setCheckedInUsers = function(checkedInUsers) {
		return $http.post(service.url + "/check-in/", checkedInUsers, service.getOptions().build()).then(function(response) {
			return response.data;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};

	this.sendEmail = function(shortUser, emailBody, date) {
		return $http.post(service.url + "/check-in/" + shortUser + "/" + date, emailBody, service.getOptions().build()).then(function(response) {
			return response.data;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};

	this.watchCheckIn = function(scope, cb) {
		var since = -1, watching = true;
		var pollChanges = function() {
			if (!watching) return;
			$http.get(service.url + "/check-in/_changes?feed=longpoll&since="+since, service.getOptions().ignoreLoadingBar().build()).then(function(response) {
				if (response.status === 200 && response.data) {
					if (response.data.results) cb(response.data.results);
					since = response.data.last_seq;
				}
				pollChanges();
			}, function() {
				$timeout(pollChanges, 5000);
			});
		};
		pollChanges();
		if (scope) {
			scope.$on('$destroy', function() {
				watching = false;
			});
		}
	};
	
	this.getEndpoints = function() {
		return $http.get(service.url + "/endpoints/", service.getOptions().build()).then(function(response) {
			return response.data;
		}, function(err) {
			service.processError(err);
			return $q(function(resolve,reject){reject(err)});
		});
	};
});
