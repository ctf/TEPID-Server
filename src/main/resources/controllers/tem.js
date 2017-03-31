angular.module('tepid')
.controller('TemCtrl', function($scope, endpoints, tepidServer) {
	$scope.endpoints = endpoints
})