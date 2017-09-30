angular.module('tepid')
.controller('CheckInCtrl', function($rootScope, $scope, $log, checkedInUsers, userOH, tepidServer) {
	$log.info(checkedInUsers);
	$log.info(userOH);
	$scope.checkedInUsers = checkedInUsers.currentCheckIn;
	$scope.userCheckedIn = $rootScope.session.user.shortUser in checkedInUsers.currentCheckIn;
	$scope.userExists = userOH.slots !== undefined;

	var weekday = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'];
	$scope.today = weekday[new Date().getDay()-1];
	$scope.userHasOH = $scope.userExists && userOH.slots[$scope.today] !== undefined;

	var timeToLiteral = function(time) {
		return ("0" + time.getHours()).slice(-2) + ':' + ("0" + time.getMinutes()).slice(-2);
	}

	var incrementHalfHour = function(timeString) {
		var timeSplit = timeString.split(':');
		var time = new Date();
		time.setHours(timeSplit[0]);
		time.setMinutes(timeSplit[1]);
		time = new Date(time.getTime() + 30*60000);
		return timeToLiteral(time);
	}

	if ($scope.userHasOH) {
		$scope.userOHDuration = userOH.slots[$scope.today];

		var findEndDate = function() {
			var start = $scope.userOHDuration[0];
			var startEndDict = {};
			if ($scope.userOHDuration.length === 1) {
				startEndDict[start] = incrementHalfHour(start);
				return startEndDict;
			}
			for (var i = 0; i < $scope.userOHDuration.length - 1; i++) {
				if (incrementHalfHour($scope.userOHDuration[i]) === $scope.userOHDuration[i+1]) {
					if (i+1 === $scope.userOHDuration.length-1) {
						startEndDict[start] = incrementHalfHour($scope.userOHDuration[i+1]);
					}
					else continue;
				}
				else {
					startEndDict[start] = incrementHalfHour($scope.userOHDuration[i]);
					start = $scope.userOHDuration[i+1];
					if (i+1 === $scope.userOHDuration.length-1) {
						startEndDict[start] = incrementHalfHour($scope.userOHDuration[i+1]);
					}
				}
			}
			return startEndDict;
		}
		$scope.startEndDict = findEndDate();
	}

	var timeHasPassed = function() {
		for (key in $scope.startEndDict) {
			if (timeToLiteral(new Date()) < incrementHalfHour(key)) {
				return false;
			}
		}
		return true;
	}

	$scope.timePassed = timeHasPassed();

	$scope.submit = function() {
		if ($scope.text) {
			if ($rootScope.session.user.shortUser in checkedInUsers.currentCheckIn) {
				tepidServer.sendEmail($rootScope.session.user.shortUser, this.text, Date());
				$scope.userCheckedIn = false;
            	delete checkedInUsers.currentCheckIn[$rootScope.session.user.shortUser];
        		tepidServer.setCheckedInUsers(checkedInUsers.currentCheckIn);
        	}
			$scope.text = '';
		}
	};

	tepidServer.watchCheckIn($scope, function(e, changes) {
		tepidServer.getCheckedInUsers().then(function(checkedInUsers) {
			$scope.checkedInUsers = checkedInUsers.currentCheckIn;
			$scope.userCheckedIn = $rootScope.session.user.shortUser in checkedInUsers.currentCheckIn;
		});
		tepidServer.getUserOH($rootScope.session.user.shortUser).then(function(userOH) {
			$scope.userHasOH = $scope.userExists && userOH.slots[$scope.today] !== undefined;
			$scope.timePassed = timeHasPassed();
		});
	});

/*	window.onbeforeunload = function (e) {
		e = e || window.event;

    	// For IE and Firefox prior to version 4
    	if (e && !$scope.timePassed && $scope.checkedIn) {
    		e.returnValue = 'Check out of your OH before you leave!';
    	}

    	// For Safari
    	return 'Check out of your OH before you leave!';
    };*/
});