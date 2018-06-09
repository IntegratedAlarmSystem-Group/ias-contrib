import org.eso.ias.heartbeat.publisher.HbKafkaProducer;
import org.eso.ias.heartbeat.serializer.HbJsonSerializer;
import org.eso.ias.plugin.Plugin;
import org.eso.ias.plugin.PluginException;
import org.eso.ias.plugin.config.PluginConfig;
import org.eso.ias.plugin.config.PluginConfigException;
import org.eso.ias.plugin.config.PluginConfigFileReader;
import org.eso.ias.plugin.config.Property;
import org.eso.ias.plugin.config.Value;
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
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.*;

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
	 * The List of the IDs of the WeatherStations monitorized by this Plugin.
	 */
	private static HashSet<Integer> stationsIds = new HashSet<>();

	/**
	 * The List of IAS Values to be sent by this Plugin.
	 */
	private static Collection<Value> iasValues;

	/**
	* Hash map with the relation between the IAS Values and the
	* Monitorized Weather Stations Ids and the corresponding sensor type.
	* They are specified in the configuration properties.
	* Example:
	*				{ WS-MeteoCentral-Temperature-Value : 3-temperature }
	*/
	private static Map<String, String> monitorPoints = new HashMap<String, String>();

	/**
	 * the loop to keep the plugin running.
	 */
	private ScheduledFuture<?> loopFuture;

	/**
	 * The weather stations pool that updates their data periodically and provides
	 * them to the plugin
	 */
	private WeatherStation weatherStation;

	/**
	 * The path to the config file for the plugin.
	 */
	private static final String configPath = "config.json";

	/**
	 * Refresh time in milliseconds
	 */
	private static final int refreshTime = 5000;

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
			"AlmaWeatherPlugin", config.getSinkServer() + ":" + config.getSinkPort(),
			new HbJsonSerializer())
		);

		// Read the IAS Values that this Plugin is monitoring from the config file
		this.iasValues = config.getValuesAsCollection();

		// Read the relation IasValueName:StationID-SensorType from the properties
		// and save it into an internal hash map
		Property[] props = config.getProperties();
		for (Property prop : props) {
			String value = prop.getValue();
			this.monitorPoints.put(prop.getKey(), value);
			this.stationsIds.add(Integer.parseInt(value.split("-")[0]));
		}
	}

	/**
	 * Connect to the Weather Stations and add the shutdown hook.
	 */
	private void initialize() {
		//weatherStation = new WeatherStation(1, 11, this.refreshTime);
		Integer[] stationsIds = this.stationsIds.toArray(new Integer[this.stationsIds.size()]);
		weatherStation = new WeatherStation(stationsIds, this.refreshTime);

		// Adds the shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(this::cleanUp, "Release weather station shutdown hook"));
	}

	/**
	 * Terminate the thread that publishes the data and disconnects from the weather
	 * station.
	 */
	private void cleanUp() {
		if (loopFuture != null) {
			loopFuture.cancel(false);
		}
		weatherStation.release();
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

			this.iasValues.forEach(value -> {
				try {
					String[] parts = monitorPoints.get(value.getId()).split("-");
					double v = weatherStation.getValue(Integer.parseInt(parts[0]), parts[1]);
					if(!Double.isNaN(v)) {
						setOperationalMode(value.getId(), OperationalMode.OPERATIONAL);
					}
					else {
						setOperationalMode(value.getId(), OperationalMode.SHUTTEDDOWN);
					}
					updateMonitorPointValue(value.getId(), v);
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

	/**
	 * runs the plugin.
	 *
	 * @param args
	 *            .
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

		logger.info("args length = " + args.length);
		if (args.length > 0) {
			sinkServer = args[0];
			if (args.length > 1) {
				try {
					sinkPort = Integer.parseInt(args[1]);
				} catch (NumberFormatException e) {
					System.err.println("Sink port" + args[1] + " must be an integer.");
					System.exit(1);
				}
			}
		}
		logger.info("Kafka sink server: " + sinkServer + ":" + sinkPort);
		config.setSinkServer(sinkServer);
		config.setSinkPort(sinkPort);

		KafkaPublisher kafkaPublisher = new KafkaPublisher(config.getId(), config.getMonitoredSystemId(),
				config.getSinkServer(), config.getSinkPort(), Plugin.getScheduledExecutorService());

		WeatherPlugin plugin = new WeatherPlugin(config, kafkaPublisher);

		try {
			plugin.start();
		} catch (PublisherException pe) {
			logger.error("The plugin failed to start", pe);
			System.exit(-3);
		}

		// Connect to the weather station.
		plugin.initialize();

		// Start getting data from the weather station
		// This method exits when the user presses CTRL+C
		// and the shutdown hook disconnects from the weather station.
		plugin.startLoop();

		logger.info("Configuration done.");
	}
}
