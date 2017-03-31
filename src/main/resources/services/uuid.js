angular.module('tepid')
.service('uuid', function() {
	var service = this;
	this.random = function() {
		return new UUID(4).format().replace(/-/g,'');
	};
});
