angular.module('tepid')
.directive('jobGrid', function(uiGridConstants) {
	return {
		restrict: 'E',
		templateUrl: 'views/job-grid.html',
		scope: true,
		link: function($scope, $element, $attrs) {
			$scope.gridOffset = $element[0].querySelector('.job-grid').getBoundingClientRect().top;
			$scope.strSplit = function(str, delim) {
				return String.prototype.split.call(str, delim);
			};
			$scope.jobGrid = {
				data: $attrs.gridModel,
				enableColumnMenus: false,
				enableHorizontalScrollbar: uiGridConstants.scrollbars.NEVER,
				excessColumns: 0,
				gridMenuShowHideColumns: false,
				rowHeight: 40,
				rowTemplate: 'grid-row.html',
				columnDefs: [
					{name: 'position', displayName: '#', type: 'number', maxWidth: 60, sort: {direction: uiGridConstants.ASC}, sortDirectionCycle: [uiGridConstants.ASC, uiGridConstants.DESC]},
					{name: 'user', maxWidth: 80, cellTemplate: 'grid-user.html'},
					{name: 'pages', maxWidth: 100, sortingAlgorithm: function(a,b){
						a=$scope.strSplit(a,' ')[0]-0;
						b=$scope.strSplit(b,' ')[0]-0;
						return a===b?0:(a<b?-1:1);
					}},
					{name: 'status', maxWidth: 100, cellTemplate: 'grid-status.html'},
					{name: 'started', maxWidth: 200},
					{name: 'host', maxWidth: 100},
					{name: 'name'},
					//~ {name: 'preview', enableSorting: false, cellTemplate: '<div class="job-preview" ng-if="COL_FIELD"><a target="_blank" href="{{COL_FIELD[0].href}}"><img ng-repeat="p in COL_FIELD" alt="Thumb {{p.i}}" src="{{p.src}}"/></a></div>'},
					{name: 'cancel', cellClass: 'menu-cell', displayName: '', enableSorting: false, maxWidth: 40, cellTemplate: 'grid-menu.html'}
				]
			};
			if ($attrs.hasOwnProperty('showQueue') && $attrs.showQueue !== 'false') {
				$scope.jobGrid.columnDefs[1] = {name: 'queue', maxWidth: 80, cellTemplate: 'grid-queue.html'};
			}
		}
	};
});
