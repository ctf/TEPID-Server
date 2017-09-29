package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.common.CheckedIn;
import ca.mcgill.science.tepid.server.rest.CheckIn.CheckedInResult;
import ca.mcgill.science.tepid.server.util.CouchClient;

import javax.ws.rs.core.MediaType;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;

public class CheckInUtils {

    private static final boolean START = true;
    private static final boolean END = false;


    /**
     * Get {@link CheckIn} for current time
     *
     * @return checkIn data
     */
    public static CheckedIn getCheckedIn() {
        List<CheckedInResult.Row> checkedInRow = CouchClient.getTepidWebTarget().request(MediaType.APPLICATION_JSON)
                .get(CheckedInResult.class).rows;
        System.out.println(checkedInRow.get(0).value);
        return checkedInRow.get(0).value;
    }

    /**
     * Check if user should be checked out at current time
     *
     * @param shortUser shortId
     * @param checkedIn those who should be checked in
     * @return true if
     */
    public static boolean userShouldCheckOut(String shortUser, CheckedIn checkedIn) {
        String endTime = checkedIn.getCurrentCheckIn().get(shortUser)[2];
        return !timeHasPassed(new String[]{endTime}, END, new ArrayList<String>());
    }

    public static boolean userLateCheckOut(String shortUser, CheckedIn checkedIn) {
        String endTime = checkedIn.getCurrentCheckIn().get(shortUser)[2];
        return !timeHasPassed(new String[]{incrementTime(endTime, 10)}, END, new ArrayList<String>());
    }

    public static boolean userCheckedIn(String shortUser, CheckedIn checkedIn) {
        return checkedIn.getCurrentCheckIn().containsKey(shortUser);
    }

    public static HashMap<String, String> initializeStartEndDict(String[] slots) {
        String start = slots[0];
        HashMap<String, String> startEndDict = new HashMap<String, String>();
        if (slots.length == 1) {
            startEndDict.put(start, incrementTime(start, 30));
            return startEndDict;
        }
        for (int i = 0; i < slots.length - 1; i++) {
            if (incrementTime(slots[i], 30).equals(slots[i + 1])) {
                if (i + 1 == slots.length - 1) {
                    startEndDict.put(start, incrementTime(slots[i + 1], 30));
                }
            } else {
                startEndDict.put(start, incrementTime(slots[i], 30));
                start = slots[i + 1];
                if (i + 1 == slots.length - 1) {
                    startEndDict.put(start, incrementTime(slots[i + 1], 30));
                }
            }
        }
        return startEndDict;
    }

    /**
     * Check if OH are done
     *
     * @param slots      slots to check
     * @param startOrEnd true for start, false for end
     * @param hours      list of hours that may be modified
     * @return true if within range, false otherwise TODO check if this is true
     */
    public static boolean timeHasPassed(String[] slots, boolean startOrEnd, ArrayList<String> hours) {
        SimpleDateFormat df = new SimpleDateFormat("HH:mm");
        Date d = new Date();
        String current = df.format(d);

        HashMap<String, String> startEndDict = initializeStartEndDict(slots);
        for (Entry<String, String> entry : startEndDict.entrySet()) {
            if (startOrEnd == START) {
                String start = entry.getKey();
                String startBefore = incrementTime(start, -15);
                String startAfter = incrementTime(start, 15);
                if (startBefore.compareTo(current) < 0 && current.compareTo(startAfter) < 0) {
                    //TODO what does this do?
                    hours.add(start);
                    hours.add(entry.getValue());

						 /* TODO There's going to be weird things happening if someone
                         * tries to check-in in the middle of consecutive time
						 * slots.... If someone has 2 hours in a row and tries to
						 * check-in in a valid time frame for second time slot, the
						 * second add call will fail..*/

                    return false;
                }
            }
            if (startOrEnd == END) {
                String end = entry.getValue();
                String endBefore = incrementTime(end, -10);
                String endAfter = incrementTime(end, 10);
                if (endBefore.compareTo(current) < 0 && current.compareTo(endAfter) < 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /*
     * TODO is time in 24 hours? Does this work properly for times at noon? Is there anyway to save time as a number rather than a String?
     */
    public static String incrementTime(String time, int increment) {
        SimpleDateFormat df = new SimpleDateFormat("HH:mm");
        Date d;
        try {
            d = df.parse(time);
        } catch (Exception e) {
            d = new Date();
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(d);
        cal.add(Calendar.MINUTE, increment);
        return df.format(cal.getTime());
    }

}
