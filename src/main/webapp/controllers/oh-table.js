angular.module('tepid')
.controller('OHTableCtrl', function($rootScope, $scope, $log, userOH, officeHours, tepidServer) {
	$log.info(userOH);
	$log.info(officeHours);
	$scope.userOH = userOH;
	$scope.officeHours = officeHours;
    $scope.undergrad = $rootScope.session.user.groups.includes("000-All Undergrad Students");

    $scope.slotsMissing = function() {
    	var numSlots = 0;
    	for (day in $scope.userOH.slots) {
    		for (slot in $scope.userOH.slots[day]) {
    			numSlots++;
    		}
    	}
    	return 6 - numSlots;
    };

    var initializeTimeLiteralArray = function() {
        var tmp = [];
        var time = new Date();
        time.setHours(9);
        time.setMinutes(0);
        var endTime = new Date();
        endTime.setHours(22);
        endTime.setMinutes(0);

        while (time.getTime() !== endTime.getTime()) {
            var timeLiteral = ("0" + time.getHours()).slice(-2) + ':' + ("0" + time.getMinutes()).slice(-2);
            tmp.push(timeLiteral);
            time = new Date(time.getTime() + 30*60000);
        }
        return tmp;
    };

    $scope.timeLiteralArray = initializeTimeLiteralArray();

    $scope.days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'];

    var timeLiteralArray = initializeTimeLiteralArray();

    var initializeSortedOfficeHours = function(nickname) {
        var sortedOH = {
            'Monday':{},
            'Tuesday':{},
            'Wednesday':{},
            'Thursday':{},
            'Friday':{}
        };
        sortedOH = initializeSortedOHArray(sortedOH);
        for (var i = 0; i < $scope.officeHours.length; i++) {
            for (day in $scope.officeHours[i].slots) {
                for (var j = 0; j < $scope.officeHours[i].slots[day].length; j++) {
                    if (nickname) {
                        if ($scope.officeHours[i].nickname.length > 10) {
                            sortedOH[day][$scope.officeHours[i].slots[day][j]].push($scope.officeHours[i].nickname.substring(0, 9) + "...");
                        }
                        else sortedOH[day][$scope.officeHours[i].slots[day][j]].push($scope.officeHours[i].nickname);
                    }
                    else sortedOH[day][$scope.officeHours[i].slots[day][j]].push($scope.officeHours[i].givenName);
                }
            }
        }
        return sortedOH;
    };

    $scope.sortedOfficeHours = initializeSortedOfficeHours(false);

    $scope.nicknameOfficeHours = initializeSortedOfficeHours(true);

    function initializeSortedOHArray(tmp) {
        for (var i = 0; i < timeLiteralArray.length; i++) {
            tmp['Monday'][timeLiteralArray[i]] = [];
            tmp['Tuesday'][timeLiteralArray[i]] = [];
            tmp['Wednesday'][timeLiteralArray[i]] = [];
            tmp['Thursday'][timeLiteralArray[i]] = [];
            tmp['Friday'][timeLiteralArray[i]] = [];
        }
        return tmp;
    };

    $scope.arrayToString = function(string){
        return string.join(", ");
    };    

    $scope.nicknameArrayToString = function(string){
        return string.join("\n");
    };

    var incrementHalfHour = function(timeString) {
        var timeSplit = timeString.split(':');
        var time = new Date();
        time.setHours(timeSplit[0]);
        time.setMinutes(timeSplit[1]);
        time = new Date(time.getTime() + 30*60000);
        return timeToLiteral(time);
    }

    var timeToLiteral = function(time) {
        return ("0" + time.getHours()).slice(-2) + ':' + ("0" + time.getMinutes()).slice(-2);
    }

    var findEndDate = function(day) {
        if (!(day in $scope.userOH.slots)) return {};
        var start = $scope.userOH.slots[day][0];
        var startEndDict = {};
        if ($scope.userOH.slots[day].length === 1) {
            startEndDict[start] = incrementHalfHour(start);
            return startEndDict;
        }
        for (var i = 0; i < $scope.userOH.slots[day].length - 1; i++) {
            var daySlots = $scope.userOH.slots[day];
            if (incrementHalfHour(daySlots[i]) === daySlots[i+1]) {
                if (i+1 === daySlots.length-1) {
                    startEndDict[start] = incrementHalfHour(daySlots[i+1]);
                }
                else continue;
            }
            else {
                startEndDict[start] = incrementHalfHour(daySlots[i]);
                start = daySlots[i+1];
                if (i+1 === daySlots.length-1) {
                    startEndDict[start] = incrementHalfHour(daySlots[i+1]);
                }
            }
        }
        return startEndDict;
    }

    var initializeUserOHTable = function() {
        if ($scope.userOH.slots === undefined) return {};
        var OHTable = {};
        for (var i = 0; i < $scope.days.length; i++) {
            OHTable[$scope.days[i]] = findEndDate($scope.days[i]);
            if (Object.keys(OHTable[$scope.days[i]]).length === 0) {
                delete OHTable[$scope.days[i]];
            }
        }
        return OHTable;
    }

    $scope.userOHTable = initializeUserOHTable();

    $scope.tableHeaders = function() {
        var headerList = ['Day'];
        var maxNum = 0;
        for (day in $scope.userOHTable) {
            maxNum = Object.keys($scope.userOHTable[day]).length > maxNum ? Object.keys($scope.userOHTable[day]).length : maxNum;
        }
        for (var i = 1; i <= maxNum; i++) {
            headerList.push('Slot ' + i);
        }
        return headerList;
    };


/*    $scope.signUp = function(day, timeSlot) {
        var tmpSignUp;
        for (var i = 0; i < officeHours.length; i++) {
            if (officeHours[i].name === userOH.name) {
                tmpSignUp = officeHours[i];
            }
        }
        if (day in tmpSignUp.slots) {
            console.log(tmpSignUp.slots);
            console.log(tmpSignUp.slots[day]);
            tmpSignUp.slots[day].push(timeSlot);
        }
        else tmpSignUp.slots.day = [timeSlot];
        console.log(tmpSignUp);
        tepidServer.setSignUp(tmpSignUp);
        userOH = tmpSignUp;
    };*/

    $scope.signUp = function(day, timeSlot, add) {
        if ($scope.userOH.slots === undefined) {
            var tmpSlots = {
                'Monday':[],
                'Tuesday':[],
                'Wednesday':[],
                'Thursday':[],
                'Friday':[]
            };
            tmpSlots[day].push(timeSlot);
            tepidServer.setSignUp($rootScope.session.user.shortUser, $rootScope.session.user.givenName, tmpSlots);
            return;
        }
        if (add) {
            $scope.userOH.slots[day].push(timeSlot);
            $scope.userOH.slots[day].sort();
        }
        else {
            $scope.userOH.slots[day].splice($scope.userOH.slots[day].indexOf(timeSlot), 1);
        }
        tepidServer.setSignUp($rootScope.session.user.shortUser, $rootScope.session.user.givenName, $scope.userOH.slots);
    };

    $scope.hoverOnButton = function() {
        this.hoverButton = true;
    }

    $scope.hoverOutButton = function() {
        this.hoverButton = false;
    }

    tepidServer.watchSignUp($scope, function(e, changes) {
        tepidServer.getSignUp($rootScope.session.user.shortUser).then(function(signup) {
            $scope.userOH = signup;
            $scope.userOHTable = initializeUserOHTable();
        });
        tepidServer.getSignUps().then(function(signups) {
            $scope.officeHours = signups;
            $scope.sortedOfficeHours = initializeSortedOfficeHours(false);
            $scope.nicknameOfficeHours = initializeSortedOfficeHours(true);
        });
    });
});
