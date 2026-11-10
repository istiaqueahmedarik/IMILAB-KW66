package dev.kw66.glass;
/** Fixed elapsed-time repeats skip missed occurrences instead of creating a burst after reboot. */
public final class SchedulePolicy {
    public static long next(long due,long now,int repeatMinutes) {
        if(repeatMinutes<=0)throw new IllegalArgumentException("Repeat period must be positive");
        long period=repeatMinutes*60_000L;
        if(now<due)return due;
        return Math.addExact(due,Math.multiplyExact((now-due)/period+1,period));
    }
}
