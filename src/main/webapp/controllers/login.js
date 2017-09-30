angular.module('tepid')
.controller('LoginCtrl', function($scope, $rootScope, $state, $timeout, $log, adminConfigured, tepidServer) {
	$scope.adminConfigured = adminConfigured;
	$scope.newAdmin = {};
	var afterLogin = function(session) {
		if (!$rootScope.afterLogin) {
			if (session.role==='user') {
				$state.go('account');
			} else {
				$state.go('dashboard');
			}
		} else {
			$state.go($rootScope.afterLogin.state, $rootScope.afterLogin.params);
			$rootScope.afterLogin = null;
		}
	};
	if ($rootScope.session) {
		afterLogin($rootScope.session);
	}
	var showError = function() {
		$scope.error = true;
		$timeout(function(){$scope.error = false;}, 5000);
	};
	$scope.loginForm = {};
	$scope.loggingIn = false;
	$scope.login = function() {
		if ($scope.loggingIn) return;
		var doLogin = function() {
			if (!$scope.loginForm.username || !$scope.loginForm.password) {
				showError();
				return;
			}
			$scope.loggingIn = true;
			tepidServer.authenticate($scope.loginForm.username, $scope.loginForm.password, !!$scope.loginForm.staySignedIn).then(function(session) {
				$scope.loginForm.username = '';
				$scope.loginForm.password = '';
				$scope.loginForm.staySignedIn = false;
				afterLogin(session);
			}, function(err) {
				showError();
				$log.error(err);
			}).finally(function() {
				$scope.loggingIn = false;
			});
		}
		if (!$scope.adminConfigured) {
			$scope.loggingIn = true;
			tepidServer.createLocalAdmin($scope.newAdmin).then(function() {
				$scope.loginForm.username = $scope.newAdmin.shortUser;
				$scope.loginForm.password = $scope.newAdmin.password;
				$scope.adminConfigured = true;
				doLogin();
			}, function(err) {
				showError();
				$scope.loggingIn = false;
				$log.error(err);
			});
		} else {
			doLogin();
		}

	};
	
});
