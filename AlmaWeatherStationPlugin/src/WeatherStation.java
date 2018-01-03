
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * keeps an array of sensors updating in parallel every refreshTime seconds, and has access to their values through
 * getValue(sensorId, parameterName).
 */
public class WeatherStation {

    public static void main(String[] args) {

        int refhreshTime = 1;
        WeatherStation ws = new WeatherStation(2, 11, refhreshTime);

        ScheduledExecutorService schedExecutorSvc = Executors.newScheduledThreadPool(
                1,
                r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    return t;
                });

        ScheduledFuture<?> loopFuture = schedExecutorSvc.scheduleAtFixedRate(
                () -> {
                    for (int i = 2; i < 12; i++) {
                        Double temperature = ws.getValue(i, "temperature");
                        System.out.println("temp" + i + ": " + temperature);
                    }
                    System.out.println();
                }, 0, 1, TimeUnit.SECONDS);

        try {
            loopFuture.get();
        } catch (Exception ce) {
            System.out.println("execution terminated");
        }
    }

    /**
     * the first sensor id in the sensor array.
     */
    private int firstId;

    /**
     * the array of sensors for this station.
     */
    private WeatherSensor[] sensors;

    /**
     * The scheduled executor to run the weather sensors in parallel.
     */
    private final ScheduledExecutorService schedExSvc = Executors.newScheduledThreadPool(
            10,
            r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });

    WeatherStation(int firstId, int lastID, int refreshTime) {
        this.firstId = firstId;
        sensors = new WeatherSensor[lastID - firstId + 1];

        for (int i = 0; i < sensors.length; i++) {
            sensors[i] = new WeatherSensor(i + firstId);

            // execute every refreshTime seconds.
            schedExSvc.scheduleAtFixedRate(sensors[i], 0, refreshTime, TimeUnit.SECONDS);
        }
        // make sure the sensors have time to update.
        sensors[0].updateValues();
    }

    /**
     * returns the requested value in the sensor with the given id, if the sensor
     * doesn't exists in this weather station ???.
     *
     * @param sensorId   of the sensor accesed.
     * @param name of the parameter requested.
     * @return the value requested, or 0 if it doesn't exist.
     */
    public double getValue(int sensorId, String name) {
        if (firstId <= sensorId && sensorId < firstId + sensors.length)
            return sensors[sensorId - firstId].getValue(name);

        // TODO: what to do with unexisting sensors? exception?
        return 0.;
    }

    /**
     * Close the connection with the weather station: terminate the threads to
     * update the values
     */
    public void release() {
        schedExSvc.shutdown();
    }
}
