angular.module('tepid')
.controller('QueueConfigCtrl', function($scope, $log, $timeout, md5, uuid, tepidServer) {
	$scope.queues = [];
	var updateQueues = function() {
		tepidServer.listQueues().then(function(queues) {
			$scope.queues = queues;
			$scope.qHash = md5.createHash(angular.toJson(queues));
		});
	};
	updateQueues();
	$scope.saveQueues = function() {
		$scope.savingQ = true;
		tepidServer.saveQueues($scope.queues).then(function(){updateQueues();$scope.savingQ=false;});
	};
	var updateDestinations = function() {
		tepidServer.listDestinations().then(function(destinations) {
			$scope.destinations = destinations;
			$scope.destHash = md5.createHash(angular.toJson(destinations));
		});
	};
	updateDestinations();
	$scope.saveDestinations = function() {
		tepidServer.saveDestinations($scope.destinations).then(function(){updateDestinations()});
	};
	$scope.$watch('destinations', function(destinations) {
		if ($scope.destHash)
		$scope.destChanged = md5.createHash(angular.toJson(destinations)) === $scope.destHash;
	}, true);
	$scope.$watch('queues', function(queues) {
		if ($scope.qHash)
		$scope.qChanged = md5.createHash(angular.toJson(queues)) === $scope.qHash;
	}, true);
	$scope.addQueue = function() {
		$scope.queues.push({"name":$scope.newQueue, _id: 'q' + $scope.newQueue, "type":'queue'});
		$scope.newQueue = '';
		$scope.enterQueue = false;
	};
	$scope.addDestination = function() {
		var id = uuid.random();
		$scope.destinations[id] = {name:$scope.newDest, protocol: "smb", type: "destination", _id: id};
		$scope.newDest = '';
		$scope.enterDest = false;
	};
	$scope.startDest = function() {
		$scope.enterDest = true;
	};
	$scope.blurDest = function() {
		$scope.enterDest = false;
	};
	$scope.startQueue = function() {
		$scope.enterQueue = true;
	};
	$scope.blurQueue = function() {
		$scope.enterQueue = false;
	};
	$scope.rename = function(o) {
		if (!o.renaming) o.renaming = true;
		else delete o.renaming;
	};
	$scope.renameKey = function(o, e) {
		if (e.keyCode === 13 || e.keyCode == 27) {
			e.preventDefault();
			$scope.finishRename(o);
			return false;
		}
	};
	$scope.finishRename = function(o) {
		delete o.renaming;
	};
	$scope.delete = function(o) {
		if (o.type === 'queue') {
			for (var i = $scope.queues.length - 1; i >= 0; i++) {
				if ($scope.queues[i]._id === o._id) $scope.queues.splice(i, 1);
				return;
			}
		} else {
			delete $scope.destinations[o._id];
		}
	};
	$scope.destMenuAnchors = {};
	$scope.openDestMenu = function(q, e) {
		if (q.destTimeout) {
			$timeout.cancel(q.destTimeout);
			delete q.destTimeout;
		}
		if (e && e.currentTarget && !e.currentTarget.classList.contains('tooltip-menu')) {
			q.destTimeout = $timeout(function(){$scope.closeDestMenu(q)}, 2000);
			$scope.destMenuAnchors[q._id] = e.currentTarget;
		}
		q.destMenuOpen = true;
	};
	$scope.closeDestMenu = function(q) {
		delete q.destMenuOpen;
		if (q.destTimeout) {
			$timeout.cancel(q.destTimeout);
			delete q.destTimeout;
		}
	};
	$scope.destMenuPos = function(q) {
		if ($scope.destMenuAnchors[q._id]) {
			var x = $scope.destMenuAnchors[q._id].offsetLeft + $scope.destMenuAnchors[q._id].offsetWidth / 2,
			y = $scope.destMenuAnchors[q._id].offsetTop + $scope.destMenuAnchors[q._id].offsetHeight;
			return {
				left: x + 'px',
				top: y + 'px'
			};
		}
		return {};
	}
	$scope.addDest = function(q, d) {
		if (q && d) {
			if (!q.destinations) q.destinations = [];
			if (q.destinations.indexOf(d._id) < 0) q.destinations.push(d._id);
		}
	};
	$scope.destAssigned = function(q, d) {
		return !!q.destinations && q.destinations.indexOf(d._id) > -1;
	};
});
