import org.eso.ias.plugin.Plugin;
import org.eso.ias.plugin.PluginException;
import org.eso.ias.plugin.config.PluginConfig;
import org.eso.ias.plugin.config.PluginConfigException;
import org.eso.ias.plugin.config.PluginConfigFileReader;
import org.eso.ias.plugin.publisher.MonitorPointSender;
import org.eso.ias.plugin.publisher.PublisherException;
import org.eso.ias.plugin.publisher.impl.KafkaPublisher;
import org.eso.ias.types.OperationalMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.*;

/**
 * publishes data from a weather station to a Kafka Queue
 */
public class WeatherPlugin extends Plugin {

  /**
   * runs the plugin.
   *
   * @param args .
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
      logger.error("Excetion opening config file", e);
      e.printStackTrace();
      System.exit(-1);
    } catch (PluginConfigException pce) {
      logger.error("Excetion reading configuratiopn", pce);
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

    logger.info("args length = "+args.length);
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
    logger.info("Kafka sink server: "+sinkServer+":"+sinkPort);
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
    plugin.setPluginOperationalMode(OperationalMode.OPERATIONAL);

    // Start getting data from the weather station
    // This method exits when the user presses CTRL+C
    // and the shutdown hook disconnects from the weather station.
    plugin.startLoop();

    logger.info("Done.");
  }

  /**
   * The logger.
   */
  private static final Logger logger = LoggerFactory.getLogger(WeatherPlugin.class);

  /**
   * the loop to keep the plugin running.
   */
  private ScheduledFuture<?> loopFuture;

  /**
   * the weather station to retrieve the data.
   */
  private WeatherStation weatherStation;

  /**
   * The path to the config file for the plugin.
   */
  private static final String configPath = "config.json";

  /**
   * Constructor
   *
   * @param config The configuration of the plugin.
   * @param sender The sender.
   */
  private WeatherPlugin(PluginConfig config, MonitorPointSender sender) {
    super(config, sender);
  }

  /**
   * Connect to the Weather Station and add the shutdown hook.
   */
  private void initialize() {
    // refreshes every 1000 milliseconds
    weatherStation = new WeatherStation(2, 11, 1000);

    // Adds the shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread(this::cleanUp, "Release weather station shutdown hook"));
  }

  /**
   * Terminate the thread that publishes the data and disconnects from the weather station.
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
   * The loop to get monitor values from the weather station
   * and update the values sent to the core of the IAS.
   */
  private void startLoop() {
    // send data every second.
    loopFuture = getScheduledExecutorService().scheduleAtFixedRate(
        () -> {
          logger.info("Updating monitor point values from the weather station");

          for (int i = 2; i < 12; i++) {
            Double temperature, windSpeed; // dewpoint, humidity, pressure, windDir;

            try {
              temperature = weatherStation.getValue(i, "temperature");
              windSpeed = weatherStation.getValue(i, "wind speed");

              updateMonitorPointValue("Temperature" + i, temperature);
              updateMonitorPointValue("WindSpeed" + i, windSpeed);

                            /*
                            humidity = weatherStation.getValue(i, "humidity");
                            pressure = weatherStation.getValue(i, "pressure");
                            dewpoint = weatherStation.getValue(i, "dewpoint");
                            windDir = weatherStation.getValue(i, "wind direction");

                            updateMonitorPointValue("Humidity" + i, humidity);
                            updateMonitorPointValue("Pressure" + i, pressure);
                            updateMonitorPointValue("Dewpoint" + i, dewpoint);
                            updateMonitorPointValue("WindDirection" + i, windDir);
                            */

            } catch (Exception e) {
              logger.error(e.getMessage());
            }
          }
          logger.info("Monitor point values updated");
        }, 0, 1, TimeUnit.SECONDS);
    try {
      loopFuture.get();
    } catch (ExecutionException ee) {
      logger.error("Execution exception getting values from the weather station", ee);
    } catch (Exception e) {
      logger.info("Loop to get monitor point values from the weather station terminated");
    }
  }
}
