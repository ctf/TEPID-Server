angular.module('tepid')
.controller('DashboardCtrl', function($scope, $rootScope, $log, $uibModal, md5, queues, destinations, tepidServer) {
	$scope.queues = queues;
	$scope.destinations = destinations;
	$scope.jobs = {};
	var refreshJobs = function() {
		var jobs = {}, done = 0;
		for (var i = 0; i < queues.length; i++) {
			(function(q) {
				tepidServer.getJobs(q.name, 10).then(function(j) {
					jobs[q.name] = j;
				}).finally(function() {
					if (++done === queues.length) {
						$scope.jobs = jobs;
						$log.info($scope.jobs);
					}
				});
			})(queues[i]);
		}
	};
	tepidServer.watchQueues($scope, function(changes) {
		tepidServer.getJobs($stateParams.queue).then(function(jobs) {
			refreshJobs();
		});
	});
	refreshJobs();
	var greetings = ['Hello','Good *morning*afternoon*evening','*Top of the mornin\'*Carpe diem*How was your day','Hi','Hey','What’s up','What’s going on','What’s new','Nice to see ya','Nice to see you','Nice to see you again','Yo','Hiya','Sup','Howdy','Always a pleasure'];
	$scope.getGreeting = function() {
		if (!$rootScope.session || !$rootScope.session.user) return 'Wait';
		var hash = md5.createHash($rootScope.session.user.shortUser + moment().format('YYYYMMDDHH')),
		ind = parseInt('0x' + hash.substr(0,2) + hash.substr(-2)) / 0xffff;
		var greeting = greetings[Math.floor(ind * greetings.length)].split(/\*/),
		g = greeting[0];
		if (greeting.length > 3) {
			var hour = moment().hour();
			if (hour < 12) {
				g += greeting[1];
			} else if (hour < 18) {
				g += greeting[2];
			} else {
				g += greeting[3];
			}
		}
		return g;
	};
	$scope.destUp = function(d) {
		var confirmDialog = $uibModal.open({
			animation: true,
			templateUrl: 'confirmUp.html',
			controller: 'DashboardCtrl.ConfirmUpCtrl',
			size: 'sm',
			resolve: {
				destination: function () {
					return $scope.destinations[d];
				}
			}
		});
		confirmDialog.result.then(function() {
			destinations[d].up = true;
			tepidServer.setDestinationUp(d, true);
		}, function () {
			$log.info('User canceled request to mark ' + $scope.destinations[d].name + ' as up');
		});
	};
	$scope.destDown = function(d) {
		var confirmDialog = $uibModal.open({
			animation: true,
			templateUrl: 'confirmDown.html',
			controller: 'DashboardCtrl.ConfirmDownCtrl',
			size: 'sm',
			resolve: {
				destination: function () {
					return $scope.destinations[d];
				}
			}
		});
		confirmDialog.result.then(function(reason) {
			destinations[d].up = false;
			tepidServer.setDestinationUp(d, false, reason);
		}, function () {
			$log.info('User canceled request to mark ' + $scope.destinations[d].name + ' as down');
		});
	};
})
.controller('DashboardCtrl.ConfirmUpCtrl', function($scope, $uibModalInstance, destination) {
	$scope.destination = destination;
	$scope.ticket = destination.ticket || null;
	$scope.reported = $scope.ticket ? moment($scope.ticket.reported).fromNow() : '';
	$scope.confirm = function() {
		$uibModalInstance.close();
	};
	$scope.cancel = function() {
		$uibModalInstance.dismiss();
	};
})
.controller('DashboardCtrl.ConfirmDownCtrl', function($scope, $uibModalInstance, destination) {
	$scope.destination = destination;
	$scope.open = true;
	$scope.reason = "";
	$scope.$watch('reason', function(reason) {
		if (reason.length > 0) {
			$scope.reason = reason.substr(0,1).toUpperCase() + reason.substr(1);
		}
	});
	$scope.confirm = function() {
		$uibModalInstance.close($scope.reason);
	};
	$scope.cancel = function() {
		$uibModalInstance.dismiss();
	};
});
