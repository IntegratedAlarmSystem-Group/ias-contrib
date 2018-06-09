import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Keeps an array of WeatherStation updating in parallel every refreshTime
 * seconds, and has access to their sensor values through
 * getValue(StationId, SensorName).
 */
public class WeatherStation {

	/**
	 * The logger.
	 */
	private static final Logger logger = LoggerFactory.getLogger(WeatherPlugin.class);

	/**
	* Number of monitorized weather stations
	*/
	private int PoolSize;

	/**
	 * the first sensor id in the sensor array.
	 */
	private int firstId;

	/**
	 * the array of weather stations that conform this WeatherStationPool
	 */
	private WeatherSensor[] stations;

	/**
	 * The scheduled executor to run the weather sensors in parallel.
	 */
	private ScheduledExecutorService schedExSvc;

	// /**
	//  * creates a list of sensors and starts running them.
	//  *
	//  * @param firstId
	//  *            first sensor.
	//  * @param lastID
	//  *            last sensor, included.
	//  * @param refreshTime
	//  *            in milliseconds.
	//  */
	// WeatherStation(int firstId, int lastID, int refreshTime) {
	// 	this.firstId = firstId;
	// 	sensors = new WeatherSensor[lastID - firstId + 1];
	//
	// 	logger.info("starting weather sensors with {}ms refresh time.", refreshTime);
	//
	// 	for (int i = 0; i < sensors.length; i++) {
	// 		sensors[i] = new WeatherSensor(i + firstId, refreshTime * 2);
	//
	// 		// execute every refreshTime seconds.
	// 		schedExSvc.scheduleAtFixedRate(sensors[i], 0, refreshTime, TimeUnit.MILLISECONDS);
	// 	}
	// 	// make sure the sensors have time to update.
	// 	sensors[0].updateValues();
	// }

	WeatherStation(Integer[] stationsIds, int refreshTime) {
		logger.info("starting weather stations with {}ms refresh time.", refreshTime);
		int poolSize = stationsIds.length;
		this.stations = new WeatherSensor[poolSize];
		this.schedExSvc = Executors.newScheduledThreadPool(poolSize, r -> {
			Thread t = new Thread(r);
			t.setDaemon(true);
			return t;
		});

		for( int i = 0; i < poolSize; i++ ){
			stations[i] = new WeatherSensor(stationsIds[i], refreshTime * 2);
			schedExSvc.scheduleAtFixedRate(stations[i], 0, refreshTime, TimeUnit.MILLISECONDS);
		}
		// make sure the sensors have time to update.
		stations[0].updateValues();
	}

	/**
	 * returns the requested value in the sensor with the given id, if the sensor
	 * doesn't exists in this weather station throws an exception..
	 *
	 * @param sensorId
	 *            of the sensor accesed.
	 * @param name
	 *            of the parameter requested.
	 * @return the value requested.
	 * @throws Exception
	 *             if the sensor doesnt exist.
	 */
	public double getValue(int sensorId, String name) throws Exception {
		if (firstId <= sensorId && sensorId < firstId + this.stations.length)
			return this.stations[sensorId - firstId].getValue(name);

		// throws exception when value not found
		throw new Exception("The station " + sensorId + " doesn't exist.");
	}

	/**
	 * Close the connection with the weather station: terminate the threads to
	 * update the values
	 */
	public void release() {
		schedExSvc.shutdown();
	}

	// public static void main(String[] args) {
	//
	// 	int refhreshTime = 1000;
	// 	WeatherStation ws = new WeatherStation(1, 11, refhreshTime);
	//
	// 	ScheduledExecutorService schedExecutorSvc = Executors.newScheduledThreadPool(1, r -> {
	// 		Thread t = new Thread(r);
	// 		t.setDaemon(true);
	// 		return t;
	// 	});
	//
	// 	ScheduledFuture<?> loopFuture = schedExecutorSvc.scheduleAtFixedRate(() -> {
	// 		for (int i = 2; i < 12; i++) {
	// 			Double temperature = null;
	//
	// 			try {
	// 				temperature = ws.getValue(i, "temperature");
	// 			} catch (Exception e) {
	// 				logger.error("error getting values", e.getMessage());
	// 			}
	//
	// 			logger.info("temp{}: {}", i, temperature);
	// 		}
	// 	}, 0, 1, TimeUnit.SECONDS);
	//
	// 	try {
	// 		loopFuture.get();
	// 	} catch (Exception e) {
	// 		logger.info("execution terminated");
	// 	}
	// }
}
