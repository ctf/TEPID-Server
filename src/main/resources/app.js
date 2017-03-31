angular.module('tepid', ['ui.router', 'ui.router.title', 'ui.grid', 'ui.grid.autoResize', 'ngAnimate', 'ui.bootstrap', 'angular-md5', 'focus-if', 'angular-loading-bar'], function($rootScopeProvider) {
	$rootScopeProvider.digestTtl(100);
})
.config(function($stateProvider, $urlRouterProvider, $locationProvider) {
	$urlRouterProvider.otherwise("/login");
	$locationProvider.html5Mode(true);
	$stateProvider
	.state('login', {
		url: "/login",
		controller : 'LoginCtrl',
		templateUrl : 'views/login.html',
		resolve: {
			adminConfigured: function(tepidServer) {
				return tepidServer.getAdminConfigured();
			}
		}
	})
	.state('queues', {
		url: "/queues",
		abstract: true,
		controller : 'QueuesCtrl',
		templateUrl : 'views/queues.html',
		data: {
			'rolesAllowed': ['user', 'ctfer', 'elder']
		},
		resolve: {
			$title: function() {
				return 'Queues';
			},
			queues: function(tepidServer) {
				return tepidServer.listQueues();
			}
		}
	})
	.state('queues.queue', {
		url: "/{queue}",
		controller : 'QueueCtrl',
		templateUrl : 'views/queue.html',
		data: {
			'rolesAllowed': ['user', 'ctfer', 'elder']
		},
		resolve: {
			$title: function($title, $stateParams) {
				return $title + ': ' + $stateParams.queue;
			},
			jobs: function($stateParams, tepidServer) {
				return tepidServer.getJobs($stateParams.queue);
			}
		}
	})
	.state('qconfig', {
		url: "/qconfig",
		controller : 'QueueConfigCtrl',
		templateUrl : 'views/qconfig.html',
		data: {
			'rolesAllowed': ['elder']
		},
		resolve: {
			$title: function() {
				return 'Queue Configuration';
			}
		}
	})
	.state('dashboard', {
		url: "/dashboard",
		controller : 'DashboardCtrl',
		templateUrl : 'views/dashboard.html',
		data: {
			'rolesAllowed': ['elder', 'ctfer']
		},
		resolve: {
			'queues': function(tepidServer) {
				return tepidServer.listQueues();
			},
			'destinations': function(tepidServer) {
				return tepidServer.listDestinations();
			},
			$title: function() {
				return 'Dashboard';
			}
		}
	})
	.state('users', {
		url: "/users",
		controller : 'UsersCtrl',
		templateUrl : 'views/users.html',
		data: {
			'rolesAllowed': ['ctfer', 'elder']
		},
		resolve: {
			$title: function() {
				return 'User Lookup';
			}
		}
	})
	.state('users.user', {
		url: "/{user}",
		controller : 'UsersCtrl.UserCtrl',
		templateUrl : 'views/user.html',
		resolve: {
			user: function($stateParams, tepidServer) {
				return tepidServer.getUser($stateParams.user).then(function(user){return user},function(req){return $stateParams.user});
			},
			quota: function($stateParams, tepidServer) {
				return tepidServer.getQuota($stateParams.user);
			},
			$title: function($title, user) {
				return $title + ': ' + user.displayName;
			}
		},
		data: {
			'rolesAllowed': ['ctfer', 'elder']
		}
	})
	.state('account', {
		url: "/account",
		controller : 'UsersCtrl.UserCtrl',
		templateUrl : 'views/user.html',
		resolve: {
			user: function($rootScope, tepidServer) {
				if (!$rootScope.session || !$rootScope.session.user) return false;
				return tepidServer.getUser($rootScope.session.user.shortUser);
			},
			quota: function($rootScope, tepidServer) {
				if (!$rootScope.session || !$rootScope.session.user) return false;
				return tepidServer.getQuota($rootScope.session.user.shortUser);
			},
			$title: function() {
				return 'My Account';
			}
		},
		data: {
			'rolesAllowed': ['ctfer', 'elder', 'user']
		}
	})
	.state('officehours', {
		url: "/officehours",
		abstract: true,
		controller : "OfficeHoursCtrl",
		templateUrl : 'views/officehours.html',
		resolve: {
			$title: function() {
				return 'Office Hours';
			}
		},
		data: {
			'rolesAllowed': ['ctfer', 'elder']
		}
	})
	.state('officehours.checkin', {
		url: "",
		controller : 'CheckInCtrl',
		templateUrl : 'views/checkin.html',
		resolve: {
			$title: function() {
				return 'Check In / Check Out';
			},
			checkedInUsers: function(tepidServer) {
				return tepidServer.getCheckedInUsers();
			},
			userOH: function($rootScope, tepidServer) {
				if (!$rootScope.session || !$rootScope.session.user) return false;
				return tepidServer.getUserOH($rootScope.session.user.shortUser);
			}
		},
		data: {
			'rolesAllowed': ['ctfer', 'elder']
		}
	})
	.state('officehours.oh-table', {
		url: "/oh-table",
		controller : 'OHTableCtrl',
		templateUrl : 'views/oh-table.html',
		resolve: {
			$title: function() {
				return 'Office Hours Table';
			},
			userOH: function($rootScope, tepidServer) {
				if (!$rootScope.session || !$rootScope.session.user) return false;
				return tepidServer.getSignUp($rootScope.session.user.shortUser);
			},
			officeHours: function(tepidServer) {
				return tepidServer.getSignUps();
			}
		},
		data: {
			'rolesAllowed': ['ctfer', 'elder']
		}
	})
	.state('constitution', {
		url: "/constitution",
		//~ controller : 'ConstitutionController',
		templateUrl : 'views/constitution.html',
		resolve: {
			$title: function() {
				return 'Constitution';
			}
		},
		data: {
			'rolesAllowed': ['ctfer', 'elder']
		}
	})
	.state('tem', {
		url: "/tem",
		controller : 'TemCtrl',
		templateUrl : 'views/tem.html',
		resolve: {
			$title: function() {
				return 'TEM';
			},
			endpoints: function($rootScope, tepidServer) {
				if (!$rootScope.session || !$rootScope.session.user) return false;
				return tepidServer.getEndpoints();
			}
		},
		data: {
			'rolesAllowed': ['ctfer', 'elder']
		}
	});
}).run(function($rootScope, $state, $window, $log, $location, tepidServer) {
	tepidServer.listQueues(); //prefetch for nav link
	var lastTop = 0;
	$rootScope.activeTop = function(scope) {
		var active = $window.document.querySelector('#page-nav .active');
		return lastTop = active ? active.offsetTop : lastTop;
	};
	$rootScope.activeNav = function(scope) {
		return !!$window.document.querySelector('#page-nav .active');
	};
	$rootScope.navOpen = true;
	$rootScope.toggleNav = function() {
		$rootScope.navOpen = !$rootScope.navOpen;
	}
	var storage = $window.sessionStorage.getItem('sessionToken') ? $window.sessionStorage : $window.localStorage;
	if (storage.getItem('sessionToken')) {
		$rootScope.sessionToken = storage.getItem('sessionToken');
		var session = $rootScope.session = angular.fromJson(storage.getItem('session'));
		$rootScope.role = $rootScope.session.role;
		tepidServer.checkSession(session.user.shortUser, session._id).then(function(sessionValid) {
			if (!sessionValid) $rootScope.logout();
		});
	}
	//initialize app here
	$rootScope.$watch('session', function(session) {
		if (session) {
			$rootScope.sessionToken = session._id;
			$rootScope.role = session.role;
			var newStorage = !session.persistent ? $window.sessionStorage : $window.localStorage;
			if (newStorage !== storage) {
				storage.removeItem('sessionToken');
				storage.removeItem('session');
				storage = newStorage;
			}
			storage.setItem('session', angular.toJson(session));
			storage.setItem('sessionToken', session._id);
			$log.info(session);
		}
	});
	$rootScope.logout = function() {
		if ($rootScope.sessionToken) {
			storage.removeItem('sessionToken');
			storage.removeItem('session');
			$rootScope.sessionToken = '';
			$rootScope.session = null;
			$rootScope.role = null;
			tepidServer.logout();
			$state.go('login');
		}
	};
	$rootScope.$on('$stateChangeSuccess', function (e, next, params) {
		$rootScope.currentState = next.name;
	});
	$rootScope.$on('$stateChangeStart', function (e, next, params) {
		var token = $window.atob($location.search().token||'').split(':'),
		user = token[0], id = token.length > 1 && token[1];
		if (user && id) tepidServer.refreshSession(user, id);
		if (!next.data || ! next.data.rolesAllowed) return;
		var rolesAllowed = next.data.rolesAllowed;
		if (!$rootScope.session) {
			e.preventDefault();
			$rootScope.afterLogin = {state: next.name, params: params || {}};
			$state.go('login');
			return;
		}
		if (rolesAllowed.indexOf($rootScope.session.role) < 0) {
			e.preventDefault();
		}
	});
});
