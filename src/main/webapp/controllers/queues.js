angular.module('tepid')
.controller('QueuesCtrl', function($scope, $rootScope, $log, $rootScope, $state, $http, queues, tepidServer) {
	$scope.queues = queues;
})
.controller('QueueCtrl', function($scope, $rootScope, $stateParams, $http, $log, jobs, tepidServer) {
	$scope.jobs = jobs;
	tepidServer.watchQueue($scope, $stateParams.queue, function(e, changes) {
		tepidServer.getJobs($stateParams.queue).then(function(jobs) {
			$scope.jobs = jobs;
		});
	});
	$scope.refund = function(job) {
		if (!job.printed) return;
		tepidServer.setJobRefunded(job._id, !job.refunded);
	};
	$scope.reprint = function(job) {
		if (!job.file) return;
		tepidServer.reprintJob(job._id);
	};
});
