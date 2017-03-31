angular.module('tepid')
.controller('UsersCtrl', function($scope, $state, $log, $timeout, tepidServer) {
	$scope.notFound = '';
	$scope.$watch('notFound', function(notFound) {
		if (notFound) {
			$timeout(function(){$scope.notFound=''}, 5000);
		}
	});
	$scope.searchUser = function() {
		$state.go('users.user', {user: $scope.user});
		$scope.user = '';
		$scope.closeAutocomplete();
		$scope.autocomplete = [];
	};
	$scope.searchAc = function() {
		$state.go('users.user', {user: $scope.autocomplete[$scope.selectedAc].shortUser});
		$scope.user = '';
		$scope.closeAutocomplete();
		$scope.autocomplete = [];
	};
	$scope.barcodeWait = null;
	$scope.scanBarcode = function(e) {
		e.preventDefault();
		var barcode = tepidServer.getBarcode();
		$scope.barcodeWait = barcode.cancel;
		barcode.req.then(function(barcode) {
			$scope.user = barcode.code;
			$scope.searchUser();
			$scope.closeAutocomplete();
			$scope.autocomplete = [];
		}).finally(function() {
			$scope.barcodeWait = null;
		});
	};
	$scope.cancelScan = function(e) {
		$scope.barcodeWait.resolve();
		$scope.barcodeWait = null;
	};
	$scope.autocompleteOpen = false;
	$scope.autocompleteOpening = false;
	$scope.autocompleteClosing = false;
	var autoPromise;
	$scope.openAutocomplete = function() {
		if ($scope.autocompleteOpen || $scope.autocompleteOpening || !$scope.autocomplete.length) return;
		if (autoPromise) $timeout.cancel(autoPromise);
		$scope.autocompleteOpening = true;
		autoPromise = $timeout(function() {
			if ($scope.autocomplete.length) $scope.autocompleteOpen = true;
			$scope.autocompleteOpening = false;
		}, 50);
	};
	$scope.closeAutocomplete = function() {
		if (!$scope.autocompleteOpen || $scope.autocompleteClosing) return;
		if (autoPromise) $timeout.cancel(autoPromise);
		$scope.autocompleteClosing = true;
		autoPromise = $timeout(function() {
			$scope.autocompleteOpen = false;
			$scope.autocompleteClosing = false;
		}, 500);
	};
	$scope.selectedAc = -1;
	$scope.selectAc = function(n) {
		$scope.selectedAc = n ;
	};
	$scope.keyDown = function(e) {
		if (e.keyCode === 38) {
			e.preventDefault();
			$scope.selectAc($scope.selectedAc > 0 ? $scope.selectedAc - 1 : $scope.autocomplete.length - 1);
		} else if (e.keyCode === 40) {
			e.preventDefault();
			$scope.selectAc(($scope.selectedAc + 1) % $scope.autocomplete.length);
		} else if (e.keyCode === 13) {
			e.preventDefault();
			if ($scope.autocompleteOpen && $scope.selectedAc >= 0) {
				$scope.searchAc();
			} else {
				$scope.searchUser();
			}
		}
	};
	var autoDelay = null, recentReq;
	$scope.$watch('user', function(user) {
		$scope.notFound='';
		if (autoDelay) {
			$timeout.cancel(autoDelay);
			autoDelay = null;
		}
		if (!user || user.length < 3 || /^\s*[0-9]+\s*$/.test(user)) {
			$scope.closeAutocomplete();
			$scope.autocomplete = [];
			return;
		}
		var reqStart = moment().valueOf();
		recentReq = reqStart;
		autoDelay = $timeout(function(started) {
			tepidServer.getUserAutoSuggest(user).then(function(completions) {
				//if recentReq is higher now, that means a new request has been made
				if (recentReq !== started || $scope.user !== user) return;
				if ($scope.selectedAc > -1 && $scope.autocomplete) $scope.selectedAc = completions.indexOf($scope.autocomplete[$scope.selectedAc]);
				$scope.autocomplete = completions;
				if ($scope.autocomplete.length) {
					$scope.openAutocomplete();
				} else {
					$scope.closeAutocomplete();
				}
			});
		}, 200, true, reqStart);
	});
})
.controller('UsersCtrl.UserCtrl', function($scope, $state, $stateParams, $location, $timeout, quota, user, tepidServer, $log, uiGridConstants) {
	if (user.substr) {
		//shortUser was passed instead of user object
		if ($scope.$parent) $scope.$parent.notFound = user;
		if ($state.current.name==='users.user') $state.go('^');
		return;
	}
	if ($state.current.name==='users.user' && user.shortUser != $stateParams.user) {
		$location.path('/users/' + user.shortUser).replace();
		return;
	}
	$scope.quota = quota;
	$scope.user = user;
	$scope.self = $state.current.name==='account';
	$scope.user.exchange = user.groups.indexOf('***REMOVED***' + moment().year() + (moment().month() < 8 ? 'W' : 'F')) > -1;
	$scope.paidFund = user.groups.indexOf('***REMOVED***') > -1;
	$scope.probationary = user.groups.indexOf('***REMOVED***') > -1;
	$scope.freshman = user.groups.indexOf('***REMOVED***') > -1;
	$scope.ctfer = $scope.probationary || (user.groups.indexOf('***REMOVED***') > -1 || (user.authType === 'local' && user.role === 'admin'));
	$scope.currentlyStudent = user.groups.indexOf('000-All Current Term Students') > -1;
	$scope.faculty = user.faculty;
	$scope.academicStaff = user.groups.indexOf('000-All Academic Staff') > -1;
	$scope.staff = user.groups.indexOf('***REMOVED***') > -1;
	$scope.department = (user.faculty||'').replace(', Department of', '').trim();
	$scope.grad = user.groups.indexOf('***REMOVED***') > -1;
	if ($scope.grad) {
		$scope.faculty = null;
		for (var i = 0; i < user.groups.length; i++) {
			var g = user.groups[i];
			if (g.startsWith('***REMOVED***')) {
				$scope.program = g.replace('***REMOVED***', '').trim();
			}
			if (g.startsWith('000-gps-')) {
				var faculty = g.replace('000-gps-', '').trim();
				if (faculty) $scope.faculty = faculty.charAt(0).toUpperCase() + faculty.substr(1);
			}
		}
		if ($scope.program===$scope.faculty) $scope.program = null;
	}
	if (user.preferredName) {
		$scope.preferredName = "";
		for (var i = user.preferredName.length - 1; i >= 0; i--) {
			$scope.preferredName += user.preferredName[i] + (i===0?"":" ");
		}
	}
	$scope.studentSince = function(user) {
		var activeSince = moment(user.activeSince);
		if (activeSince.isBefore('2002-09-01')) return "Before 2002";
		return activeSince.format("MMMM YYYY");
	};
	var showTimeout;
	$scope.showId = function() {
		if (showTimeout) {
			$timeout.cancel(showTimeout);
			showTimeout = null;
		}
		if (!$scope.showingId) {
			$scope.showingId = true;
			showTimeout = $timeout(function() {
				$scope.showingId = false;
			}, 10000);
		} else {
			$scope.showingId = false;
		}
	};
	$scope.setExchange = function() {
		tepidServer.setExchange(user.shortUser, user.exchange);
	};
	$scope.refund = function(job) {
		if (!job.printed) return;
		tepidServer.setJobRefunded(job._id, !job.refunded).then(function() {
			tepidServer.getJobsByUser($scope.user.shortUser).then(function(jobs) {
				$scope.jobs = jobs;
			});
		});
	};
	$scope.reprint = function(job) {
		if (!job.file) return;
		tepidServer.reprintJob(job._id).then(function() {
			tepidServer.getJobsByUser($scope.user.shortUser).then(function(jobs) {
				$scope.jobs = jobs;
			});
		});
	};
	if ($scope.paidFund || $scope.ctfer) {
		tepidServer.getJobsByUser($scope.user.shortUser).then(function(jobs) {
			$scope.jobs = jobs;
		});
	}
});
