import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class WeatherStationsPoolExample {

  /**
   * The logger.
   */
  private static final Logger logger = LoggerFactory.getLogger(WeatherPlugin.class);

  public static void main(String[] args) {

  	int refreshTime = 5000;
    Integer[] stationsIds = {1, 2, 5};

  	WeatherStationsPool ws = new WeatherStationsPool(stationsIds, refreshTime);

    // Example with one thread
  	ScheduledExecutorService schedExecutorSvc = Executors.newScheduledThreadPool(1, r -> {
  		Thread t = new Thread(r);
  		t.setDaemon(true);
  		return t;
  	});

    // get value of "temperature" sensor periodically from each weather station
    // in the stationsIds array and write them in the log
  	ScheduledFuture<?> loopFuture = schedExecutorSvc.scheduleAtFixedRate(() -> {
  		for (int i = 0; i < stationsIds.length ; i++) {
  			Double temperature = null;
  			try {
  				temperature = ws.getValue(stationsIds[i], "temperature");
  			} catch (Exception e) {
  				logger.error("error getting values", e.getMessage());
  			}
  			logger.info("temp{}: {}", stationsIds[i], temperature);
  		}
  	}, 0, refreshTime, TimeUnit.MILLISECONDS);

  	try {
  		loopFuture.get();
  	} catch (Exception e) {
  		logger.info("execution terminated");
  	}
  }

}
