package org.eso.ias.plugin;

import ch.qos.logback.classic.LoggerContext;
import org.eso.ias.heartbeat.publisher.HbKafkaProducer;
import org.eso.ias.heartbeat.serializer.HbJsonSerializer;
import org.eso.ias.plugin.Plugin;
import org.eso.ias.plugin.PluginException;
import org.eso.ias.plugin.config.PluginConfig;
import org.eso.ias.plugin.config.PluginConfigFileReader;
import org.eso.ias.plugin.config.Value;
import org.eso.ias.plugin.publisher.MonitorPointSender;
import org.eso.ias.plugin.publisher.PublisherException;
import org.eso.ias.plugin.publisher.impl.KafkaPublisher;
import org.eso.ias.types.OperationalMode;
import org.slf4j.LoggerFactory;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.io.File;



/**
 * publishes data to a Kafka Queue
 */
public class MultiDummyPlugin extends Plugin {

  private double value = 0;
  private String valueId;
  private int updateTime = 1000;

  /**
   * runs the plugin.
   */
  public static void main(String[] args) throws Exception {
    System.err.println("Starting dummy plugin...");

    // stop logging
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    loggerContext.stop();
    System.err.println("Stopped logging");

    // IASIO
    int refreshTime = 1000;



    // configuration
      File f =new File("dummy-config.json");
    PluginConfigFileReader configReader=new  PluginConfigFileReader(f);
    Future<PluginConfig> future=configReader.getPluginConfig();

    PluginConfig config = future.get();

    Value[] values=config.getValues();

    String s1 = values[0].getId();
    String id = s1;


    // publisher
    KafkaPublisher publisher = new KafkaPublisher(config.getId(),
        config.getMonitoredSystemId(),
        config.getSinkServer(),
        config.getSinkPort(),
        Plugin.getScheduledExecutorService());


    // start plugin
    MultiDummyPlugin dummy = new MultiDummyPlugin(config, publisher);
    dummy.valueId = id;
    try {
      dummy.start();
    } catch (PublisherException pe) {
      System.err.println("The plugin failed to start");
      pe.printStackTrace(System.err);
      System.exit(-3);
    }

    // set mode
    dummy.setPluginOperationalMode(OperationalMode.OPERATIONAL);
    dummy.startLoop();






    // instructions
    System.err.println("Plugin started, sending value 0. waiting for user input...");

    System.err.println("\nCurrent ID: " + s1);
    System.err.println("Available commands:");

    System.err.println("  > value [double]");
    System.err.println("  Changes the value the plugin is sending.");
    System.err.println("  By default, any value outside ]0,50[ will trigger the alarm.");
    System.err.println("  Default value is: 0.0.\n");

    System.err.println("  > mode [mode]");
    System.err.println("  Changes the operational mode of the plugin. The options are:");
    System.err.println("  operational, maintenance, startup, initialization, degraded, closing, shutteddown and unknown.");
    System.err.println("  Default mode is: operational.\n");

    System.err.println("  > update [int]");
    System.err.println("  Changes the rate at wich the value in the plugin is updated (milliseconds).");
    System.err.println("  If this value is higher than 1500 the value sent will be invalid.");
    System.err.println("  Default refresh time  is: " + refreshTime +  "ms.\n");

    System.err.println("  > id [String]");
    System.err.println("  Changes the Plugin's ID.\n");
    System.err.println("  > All available IDs are: ");
      for(Value val : values) {
          System.err.println("     " + val.getId());
      }

    System.err.println("\n  > current");
    System.err.println("  Prints the current ID\n");




    // start reading values from input
    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    String line;
    while ((line = br.readLine()) != null) {
      String[] arg = line.split(" ");

      if (arg.length > 2) {
        System.err.println(">>Invalid expression: " + line);
        continue;
      }

      switch (arg[0].toLowerCase()) {



          // modify value
        case "value":
          try {
            Double value = Double.parseDouble(arg[1]);
            if(dummy.value == value){
                System.err.println(">>value is already set to " + value + "!");
            }else{
            dummy.value = value;
            dummy.updateMonitorPointValue(dummy.valueId, value);

            System.err.println(">>" + id + " value updated to " + value);}
          } catch (Exception e) {
            System.err.println(">>Invalid value: " + arg[1]);
          }
          break;

        // operational mode
        case "mode":
          String msg = ">>operational mode changed to: ";

          switch (arg[1].toLowerCase()) {
            case "operational":
              dummy.setPluginOperationalMode(OperationalMode.OPERATIONAL);
              System.err.println(msg + arg[1]);
              break;

            case "maintenance":
              dummy.setPluginOperationalMode(OperationalMode.MAINTENANCE);
              System.err.println(msg + arg[1]);
              break;

            case "startup":
              dummy.setPluginOperationalMode(OperationalMode.STARTUP);
              System.err.println(msg + arg[1]);
              break;

            case "initialization":
              dummy.setPluginOperationalMode(OperationalMode.INITIALIZATION);
              System.err.println(msg + arg[1]);
              break;

            case "degraded":
              dummy.setPluginOperationalMode(OperationalMode.DEGRADED);
              System.err.println(msg + arg[1]);
              break;

            case "closing":
              dummy.setPluginOperationalMode(OperationalMode.CLOSING);
              System.err.println(msg + arg[1]);
              break;

            case "shutteddown":
              dummy.setPluginOperationalMode(OperationalMode.SHUTTEDDOWN);
              System.err.println(msg + arg[1]);
              break;

            case "unknown":
              dummy.setPluginOperationalMode(OperationalMode.UNKNOWN);
              System.err.println(msg + arg[1]);
              break;

            default:
              System.err.println(">>unrecongnized operational mode: " + arg[1]);
              break;
          }
          break;

        // update time
        case "update":
          try {
            Integer value = Integer.parseInt(arg[1]);
            dummy.updateTime = value;

            // restart loop
            dummy.loopFuture.cancel(true);
            dummy.startLoop();

            System.err.println(">>update time changed to " + value + "ms");
          } catch (Exception e) {
            System.err.println(">>Invalid update time: " + arg[1]);
          }
          break;



          // change ID
          case "id":


              boolean found=false;
              for (int i=0;i<values.length;i=i+1){
                if(values[i].getId().equals(arg[1])){
                    dummy.valueId = values[i].getId();
                    System.err.println("ID changed to: " + arg[1]);
                    found=true;
                    break;
                }
              }
              if (!found){
                  System.err.println("ID: " + arg[1] + " does not exist.");
              }
              break;


          case "current":
              System.err.println(">>The current ID is: " + dummy.valueId);
              break;


          default:
          System.err.println(">>unrecognized command: " + line);
          break;
      }
    }

    br.close();
    System.err.println("Closing plugin");

    try {
      dummy.loopFuture.cancel(true);
    } catch (Exception e) {
      System.err.println("loop terminated");
    }
  }

  /**
   * the loop to keep the plugin running.
   */
  private ScheduledFuture<?> loopFuture;

  private MultiDummyPlugin(PluginConfig config, MonitorPointSender sender) {
    super(config, sender, new HbKafkaProducer(
			config.getId(), config.getSinkServer() + ":" + config.getSinkPort(),
			new HbJsonSerializer())
		);
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
      System.err.println("Error sending " + mPointID + " monitor point to the core of the IAS");
      pe.printStackTrace();
    }
  }

  /**
   * The loop to update the value every 1 second
   */
  private void startLoop() {
    // send data every second.
    loopFuture = getScheduledExecutorService().scheduleAtFixedRate(
        () -> updateMonitorPointValue(valueId, value), 0, updateTime, TimeUnit.MILLISECONDS);
  }
}