package  org.eso.ias.contrib.plugin.weather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
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
	private static final Logger logger = LoggerFactory.getLogger(WeatherPlugin.class);

	/**
	 * the HashMap of weather stations that conform this WeatherStationsPool
	 */
	private static Map<Integer, WeatherStation> stations = new HashMap<Integer, WeatherStation>();

	/**
	 * The scheduled executor to run the weather stations in parallel.
	 */
	private ScheduledExecutorService schedExSvc;

	/**
	 * The Constructor
	 *
	 * @param stationsIds
	 *								An array of integers corresponding to the ids of the
	 *								weather stations monitorized by this WeatherStationsPool
	 *
	 * @param refreshTime
	 *								The period of time used to schedule the weather station
	 *								updates in milliseconds.
	 */
	WeatherStationsPool(Integer[] stationsIds, int refreshTime) {
		logger.info("starting weather stations with {}ms refresh time.", refreshTime);
		int poolSize = stationsIds.length;
		this.schedExSvc = Executors.newScheduledThreadPool(poolSize, r -> {
			Thread t = new Thread(r);
			t.setDaemon(true);
			return t;
		});
		for( int i = 0; i < poolSize; i++ ){
			int id = stationsIds[i];
			this.stations.put(id, new WeatherStation(id, refreshTime * 2));
			this.schedExSvc.scheduleAtFixedRate(this.stations.get(id), 0, refreshTime, TimeUnit.MILLISECONDS);
		}
		// make sure the sensors have time to update.
		try{
			TimeUnit.MILLISECONDS.sleep(refreshTime);
		}
		catch( Exception e){
			logger.info("Some values might be invalid because of startup, they should be valid in some seconds.");
		}
	}

	/**
	 * returns the requested value in the station with the given id, if the station
	 * doesn't exists in this weather station pool throws an exception..
	 *
	 * @param stationId
	 *            of the station accesed.
	 * @param name
	 *            the name of the sensor requested.
	 * @return the value requested.
	 * @throws Exception
	 *             if the station doesnt exist.
	 */
	public double getValue(int stationId, String name) throws Exception {
		WeatherStation station = this.stations.get(stationId);
		if (station != null){
			return station.getValue(name);
		}

		// throws exception when station was not found
		throw new Exception("The station " + stationId + " doesn't exist.");
	}

	/**
	 * Close the connection with the weather station: terminate the threads to
	 * update the values
	 */
	public void release() {
		schedExSvc.shutdown();
	}

}
