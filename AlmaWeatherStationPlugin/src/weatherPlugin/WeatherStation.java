package weatherPlugin;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class WeatherStation {

    public static void main(String[] args) {

        int refhreshTime = 1;
        WeatherStation ws = new WeatherStation(2, 11, refhreshTime);

        ScheduledExecutorService schedExecutorSvc = Executors.newScheduledThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            }
        });

        ScheduledFuture<?> loopFuture = schedExecutorSvc.scheduleAtFixedRate(new Runnable() {
            public void run() {
                for (int i = 2; i < 12; i++) {
                    Double temperature = ws.getValue(i, "temperature");
                    System.out.println("temp" + i + ": " + temperature);
                }
                System.out.println();
            }
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
    int firstId;

    /**
     * the array of sensors for this station.
     */
    WeatherSensor[] sensors;

    /**
     * The scheduled executor to update the values of the monitored points
     */
    private final ScheduledExecutorService schedExSvc = Executors.newScheduledThreadPool(10, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        }
    });

    public WeatherStation(int firstId, int lastID, int refreshTime) {
        this.firstId = firstId;
        sensors = new WeatherSensor[lastID - firstId + 1];

        for (int i = 0; i < sensors.length; i++) {
            // this may take some time as it sequentially updates every sensor.
            sensors[i] = new WeatherSensor(firstId + i);
        }

        for (int i = 0; i < sensors.length; i++) {
            // execute every refreshTime seconds.
            schedExSvc.scheduleAtFixedRate(sensors[i], 0, refreshTime, TimeUnit.SECONDS);
        }
    }

    /**
     * returns the requested value in the sensor with the given id, if the sensor
     * doesn't exists in this weather station ???.
     *
     * @param id
     * @param name
     * @return
     */
    public double getValue(int id, String name) {
        if (firstId <= id && id < firstId + sensors.length)
            return sensors[id - firstId].getValue(name);

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
