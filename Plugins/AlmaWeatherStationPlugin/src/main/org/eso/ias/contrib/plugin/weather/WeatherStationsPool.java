package  org.eso.ias.contrib.plugin.weather;

import org.eso.ias.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps an array of WeatherStations updating in parallel every refreshTime
 * seconds, and has access to their sensor values through
 * getValue(StationId, SensorName).
 */
public class WeatherStationsPool {

	/**
	 * The logger.
	 */
	private static final Logger logger = LoggerFactory.getLogger(WeatherStationsPool.class);

	/**
	 * the HashMap of weather stations that conform this WeatherStationsPool
	 */
	private static Map<String, WeatherStation> stations = new HashMap<>();

	/**
	 * The scheduled executor to run the weather stations in parallel.
	 */
	private ScheduledExecutorService schedExSvc;

	/**
	 * The Constructor
	 *
	 * @param stationsIds
	 *								An array of Strings and WS SOAP ids corresponding to the ids of the
	 *								weather stations monitorized by this WeatherStationsPool
     *							    like MeteoCentral
	 *
	 * @param plugin                The plugin to send new sample
	 */
	WeatherStationsPool(Map<String,Integer> stationsIds, Plugin plugin) {
		logger.info("starting weather stations ");
		this.schedExSvc = Executors.newScheduledThreadPool(stationsIds.size(), r -> {
			Thread t = new Thread(r);
			t.setDaemon(true);
			return t;
		});

        stationsIds.keySet().forEach( id -> {
            this.stations.put(id, new WeatherStation(id, stationsIds.get(id),plugin));
            this.schedExSvc.scheduleAtFixedRate(this.stations.get(id), 0, 1, TimeUnit.SECONDS);
            logger.info("WS {} with id {} instantiated and loop activated",id,stationsIds.get(id));
        });
	}

	/**
	 * Close the connection with the weather station: terminate the threads to
	 * update the values
	 */
	public void release() {
		schedExSvc.shutdown();
		logger.info("Executor is shut down");
	}

}
