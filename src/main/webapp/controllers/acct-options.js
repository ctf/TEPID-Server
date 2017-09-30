angular.module('tepid')
.controller('AcctOptionsCtrl', function($scope, tepidServer) {
	$scope.user = $scope.$parent.user;
	$scope.user.acceptDisclaimer = !!$scope.user.colorPrinting;
	$scope.toggleColor = function() {
		$scope.user.colorPrinting = !$scope.user.colorPrinting;
		if (!$scope.user.colorPrinting) $scope.user.acceptDisclaimer = false;
	};
	$scope.$watch('user.acceptDisclaimer', function(accept, before) {
		if (accept !== before) tepidServer.setColorPrinting($scope.user.shortUser, accept);
	});
	$scope.savingNick = false;
	$scope.changeNick = function() {
		if ($scope.savingNick) return;
		$scope.savingNick = true;
		var nick = $scope.user.nick || null;
		tepidServer.setNick($scope.user.shortUser, nick).finally(function() {
			$scope.savingNick = false;
			$scope.user.salutation = nick;
		});
	};
	$scope.keyDown = function(e) {
		if (e.keyCode === 13) {
			e.preventDefault();
			e.target.blur();
		}
	};
	//1 month defined as 365 days divided by 12
	$scope.expirationOptions = {"1 Day": 86400000, "1 Week": 604800000, "1 Month": 2628000000, "3 Months": 7884000000};
	$scope.formatExpiration = function(time) {
		for (var k in $scope.expirationOptions) {
			if ($scope.expirationOptions[k] === time) return k;
		}
		return "";
	};
	$scope.setExpiration = function(time) {
		$scope.user.jobExpiration = time;
		tepidServer.setJobExpiration($scope.user.shortUser, time);
	};
});
