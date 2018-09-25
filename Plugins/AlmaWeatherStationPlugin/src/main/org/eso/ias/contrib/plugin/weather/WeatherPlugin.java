package  org.eso.ias.contrib.plugin.weather;

import org.eso.ias.heartbeat.publisher.HbKafkaProducer;
import org.eso.ias.heartbeat.serializer.HbJsonSerializer;
import org.eso.ias.plugin.Plugin;
import org.eso.ias.plugin.PluginException;
import org.eso.ias.plugin.config.PluginConfig;
import org.eso.ias.plugin.config.PluginConfigException;
import org.eso.ias.plugin.config.PluginConfigFileReader;
import org.eso.ias.plugin.config.Property;
import org.eso.ias.plugin.publisher.MonitorPointSender;
import org.eso.ias.plugin.publisher.PublisherException;
import org.eso.ias.plugin.publisher.impl.KafkaPublisher;
import org.eso.ias.types.OperationalMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * IAS Plugin that runs in a never-ending loop and publishes data obtained
 * periodically from a set of weather stations to a Kafka Queue
 */
public class WeatherPlugin extends Plugin {

	/**
	 * The logger.
	 */
	private static final Logger logger = LoggerFactory.getLogger(WeatherPlugin.class);

	/**
	 * The Set of IDs of the Weather Stations monitorized by this Plugin. It is a
	 * Set to avoid duplication on setup.
	 */
	private static HashSet<Integer> stationsIds = new HashSet<>();

	/**
	 * The Collection of IAS Values to be sent by this Plugin.
	 */
	private static Collection<String> valuesIds;

	/**
	* Hash map with the relation between the IAS Values and the
	* Monitorized Weather Stations Ids and the corresponding sensor type.
	* The keys of the hashMap are the IAS Values Ids and the values are strings
	* with the format StationID-SensorType
	* They must be specified in the configuration properties as follow:
	*		 {
	*      "key": "WS-MeteoOSF-Temperature-Value",
	*      "value": "3-temperature"
	*    }
	*/
	private static HashMap<String, String> identifiersMap = new HashMap<String, String>();

	/**
	 * the loop to keep the plugin running.
	 */
	private ScheduledFuture<?> loopFuture;

	/**
	 * The weather stations pool that updates their data periodically and provides
	 * them to the plugin
	 */
	private WeatherStationsPool weatherStationsPool;

	/**
	 * The path to the config file for the plugin.
	 */
	private static final String configPath = "config.json";

	/**
	 * Refresh time in seconds
	 */
	private final int refreshTime;

	/**
	 * The name of the property to pass the kafka servers to connect to
	 */
	private static final String KAFKA_SERVERS_PROP_NAME = "org.eso.ias.plugins.kafka.server";


	/**
	 * Constructor
	 *
	 * @param config
	 *            The configuration of the plugin.
	 * @param sender
	 *            The sender.
	 */
	private WeatherPlugin(PluginConfig config, MonitorPointSender sender) {
		// Connection with the Kafka healthyness queue to report its own health state
		super(config, sender, new HbKafkaProducer(
			"AlmaWeatherPlugin"+"HBSender", config.getSinkServer() + ":" + config.getSinkPort(),
			new HbJsonSerializer())
		);

		this.refreshTime = config.getAutoSendTimeInterval();
        logger.info("Refresh rate {} secs",refreshTime);

		// Read the IAS Values that this Plugin is monitoring from the config file
		this.valuesIds = config.getValuesAsCollection().stream().map(value -> value.getId()).collect(Collectors.toList());

		// Read the relation IasValueID:StationID-SensorType from the properties
		// and save it into an internal hash map
		Property[] props = config.getProperties();
		for (Property prop : props) {
			String value = prop.getValue();
			this.identifiersMap.put(prop.getKey(), value);
			this.stationsIds.add(Integer.parseInt(value.split("-")[0]));
		}
	}

	/**
	 * Connect to the Weather Stations and add the shutdown hook.
	 */
	private void initialize() {
		Integer[] stationsIds = this.stationsIds.toArray(new Integer[this.stationsIds.size()]);
		weatherStationsPool = new WeatherStationsPool(stationsIds, this.refreshTime);

		// Adds the shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(this::cleanUp, "Release weather station shutdown hook"));
	}

	/**
	 * Terminate the thread that publishes the data and disconnects from the weather
	 * station pool.
	 */
	private void cleanUp() {
		if (loopFuture != null) {
			loopFuture.cancel(false);
		}
		weatherStationsPool.release();
	}

	/**
	 * Override method to catch the exception and log a message
	 * <p>
	 * In the example we do not take any special action if the Plugin returns an
	 * error when submitting a new value.
	 */
	@Override
	public void updateMonitorPointValue(String mPointID, Object value) {
		try {
			super.updateMonitorPointValue(mPointID, value);
		} catch (PluginException pe) {
			logger.error("Error sending {} monitor point to the core of the IAS", mPointID);
		}
	}

	/**
	 * The loop to get monitor values from the weather station and update the values
	 * sent to the core of the IAS.
	 */
	private void startLoop() {
		// send data every second.
		loopFuture = getScheduledExecutorService().scheduleAtFixedRate(() -> {
			logger.debug("Updating monitor point values from the weather station");

			this.valuesIds.forEach(valueId -> {
				try {
					String[] parts = this.identifiersMap.get(valueId).split("-");
					int stationId = Integer.parseInt(parts[0]);
					String sensorName = parts[1];

					double value = this.weatherStationsPool.getValue(stationId, sensorName);
					if(value == -Double.MAX_VALUE) {
						value = Double.parseDouble("NaN");
						setOperationalMode(valueId, OperationalMode.SHUTTEDDOWN);
					}
					else {
						setOperationalMode(valueId, OperationalMode.OPERATIONAL);
					}
					updateMonitorPointValue(valueId, value);
				} catch (Exception e) {
					logger.error(e.getMessage());
				}
			});

			logger.debug("Monitor point values updated");
		}, 0, 1, TimeUnit.SECONDS);
		try {
			loopFuture.get();
		} catch (ExecutionException ee) {
			logger.error("Execution exception getting values from the weather station", ee);
		} catch (Exception e) {
			logger.warn("Loop to get monitor point values from the weather station terminated");
		}
	}

	private static void printUsage() {
		System.out.println("Usage: java - jar alma-weather-plugin.jar");
	}

	/**
	 * Runs the plugin. The sinkserver and port can be overrided using the java
	 * property. Args are optional and they override the refresh time.
	 *
	 * @param args
	 *          [0] optional -refresh
	 *					[1] optional REFRESH-TIME-MILLIS
	 */
	public static void main(String[] args) {

		logger.info("Started...");
		PluginConfig config = null;
		try {
			File configFile = new File(configPath);
			PluginConfigFileReader jsonFileReader = new PluginConfigFileReader(configFile);
			Future<PluginConfig> futurePluginConfig = jsonFileReader.getPluginConfig();
			config = futurePluginConfig.get(1, TimeUnit.MINUTES);
		} catch (FileNotFoundException e) {
			logger.error("Exception opening config file", e);
			e.printStackTrace();
			System.exit(-1);
		} catch (PluginConfigException pce) {
			logger.error("Exception reading configuration", pce);
			pce.printStackTrace();
			System.exit(-1);
		} catch (InterruptedException ie) {
			logger.error("Interrupted", ie);
			ie.printStackTrace();
			System.exit(-1);
		} catch (TimeoutException te) {
			logger.error("Timeout reading configuration", te);
			te.printStackTrace();
			System.exit(-1);
		} catch (ExecutionException ee) {
			ee.printStackTrace();
			logger.error("Execution error", ee);
			System.exit(-1);
		}
		logger.info("Configuration successfully read");

		String sinkServer = config.getSinkServer();
		int sinkPort = config.getSinkPort();

		/*Kafka server and port can be set using the Java properties*/
		String kServers= System.getProperty(KAFKA_SERVERS_PROP_NAME);
		if (kServers!=null && !kServers.isEmpty()) {
			try{
				sinkServer = kServers.split(":")[0];
				sinkPort = Integer.parseInt(kServers.split(":")[1]);
				config.setSinkServer(sinkServer);
				config.setSinkPort(sinkPort);
			} catch (NumberFormatException e) {
				printUsage();
				System.exit(-2);
			}
		}
		logger.info("Kafka sink server: " + sinkServer + ":" + sinkPort);

		// Instantiate a KafkaPublisher
		KafkaPublisher kafkaPublisher = new KafkaPublisher(config.getId(), config.getMonitoredSystemId(),
				config.getSinkServer(), config.getSinkPort(), Plugin.getScheduledExecutorService());

		// Instantiate the WeatherPlugin
		WeatherPlugin plugin = new WeatherPlugin(config, kafkaPublisher);

		// Start the Plugin
		try {
			plugin.start();
		} catch (PublisherException pe) {
			logger.error("The plugin failed to start", pe);
			System.exit(-3);
		}

		// Connect to the Weather Stations.
		plugin.initialize();

		// Start getting data from the weather stations pool
		// This method exits when the user presses CTRL+C
		// and the shutdown hook disconnects from the weather station.
		plugin.startLoop();

		logger.info("Configuration done.");
	}
}
