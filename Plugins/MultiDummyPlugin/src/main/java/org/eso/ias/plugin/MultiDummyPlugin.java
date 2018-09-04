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
import java.util.HashMap;
import java.util.Set;
import java.io.File;



/**
 * publishes data to a Kafka Queue
 */
public class MultiDummyPlugin extends Plugin {

  private double value = 0;
  private String valueId;
  private int updateTime = 1000;
  private HashMap<String, Double> hm;


    /**
   * runs the plugin.
   */

  // method to print out instructions
  public static void instructions(Value[] arrayOfValues) {
      System.err.println("Available commands:");

      System.err.println("  > value [double]");
      System.err.println("  Changes the value the plugin is sending.");
      System.err.println("  By default, any value outside ]0,50[ will trigger the alarm.\n");

      System.err.println("  > mode [mode]");
      System.err.println("  Changes the operational mode of the plugin. The options are:");
      System.err.println("  operational, maintenance, startup, initialization, degraded, closing, shutteddown and unknown.\n");

      System.err.println("  > update [int]");
      System.err.println("  Changes the rate at wich the value in the plugin is updated (milliseconds).");
      System.err.println("  If this value is higher than 1500 the value sent will be invalid.\n");

      System.err.println("  > id [String]");
      System.err.println("  Changes the Plugin's ID.");
      System.err.println("  All available IDs are: ");
      for (Value val : arrayOfValues) {
          System.err.println("     " + val.getId());
      }

      System.err.println("\n  > allvalue [double]");
      System.err.println("  Changes all IDs to the same value.\n");

      System.err.println("  > change [String] to value [double]");
      System.err.println("  Changes the specified ID to the specified value.");
      System.err.println("  > change [String] to mode [mode]");
      System.err.println("  Changes the specified ID to the specified operational mode.");
      System.err.println("  > change [String] to UpdateTime [int]");
      System.err.println("  Changes the specified ID to the specified update time.\n");

      System.err.println("  > current");
      System.err.println("  Prints the current ID.\n");

      System.err.println("  > ?");
      System.err.println("  Shows all available commands.\n");

      // all value x (done)

      // stop value

      // up to 30 ids

      // unreliable bug (fixed)

      // optional requirement

  }

  public static void main(String[] args) throws Exception {
    System.err.println("Starting dummy plugin...");

    // stop logging
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    loggerContext.stop();
    System.err.println("Stopped logging");

    // IASIO
    int refreshTime = 1000;


    // configuration
    File f =new File(args[0]);
    System.err.println("File used: " + args[0]);
    PluginConfigFileReader configReader=new  PluginConfigFileReader(f);
    Future<PluginConfig> future=configReader.getPluginConfig();

    PluginConfig config = future.get();

    HashMap<String, Double> valueMapping = new HashMap<String, Double>();
    Value[] values=config.getValues();
    for (Value v : values ){
        valueMapping.put(v.getId(), 0.0);
    }

    // publisher
    KafkaPublisher publisher = new KafkaPublisher(config.getId(),
        config.getMonitoredSystemId(),
        config.getSinkServer(),
        config.getSinkPort(),
        Plugin.getScheduledExecutorService());



    // start plugin
    MultiDummyPlugin dummy = new MultiDummyPlugin(config, publisher, valueMapping);
    dummy.valueId = values[0].getId();
    dummy.value = valueMapping.get(dummy.valueId);


    try {
      dummy.start();
    } catch (PublisherException pe) {
      System.err.println("The plugin failed to start");
      pe.printStackTrace(System.err);
      System.exit(-3);
    }

    // set mode
    HashMap<String, OperationalMode> modeMapping = new HashMap<String, OperationalMode>();
    for (Value m : values){
        modeMapping.put(m.getId(), OperationalMode.OPERATIONAL);
    }
    OperationalMode idMode = modeMapping.get(values[0].getId());
      for (Value n : values){
          dummy.setOperationalMode(n.getId(), idMode);
      }

    dummy.startLoop();


    // instructions
      System.err.println("Plugin started, sending value 0. waiting for user input...");
      System.err.println("\nCurrent ID: " + dummy.valueId);
      System.err.println("Default value is: 0.0.");
      System.err.println("Default mode is: operational.");
      instructions(values);


    // start reading values from input
    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    String line;
    while ((line = br.readLine()) != null) {
      String[] arg = line.split(" ");

      if (arg.length > 5) {
        System.err.println(">>Invalid expression: " + line);
        continue;
      }

      switch (arg[0].toLowerCase()) {

          // modify value
        case "value":
          try {
            Double value = Double.parseDouble(arg[1]);
            if(dummy.value == value){
                System.err.println(">>" + dummy.valueId + " value is already set to " + value + "!");
            } else {
            valueMapping.put(dummy.valueId, value);
            dummy.value = valueMapping.get(dummy.valueId);
            System.err.println(">>" + dummy.valueId + " value updated to: " + value);
            }
          } catch (Exception e) {
            System.err.println(">>Invalid value: " + arg[1]);
          }
          break;


        // operational mode
        case "mode":
          String msg = ">>" + dummy.valueId + " operational mode changed to: ";
          modeMapping.put(dummy.valueId, OperationalMode.valueOf(arg[1].toUpperCase()));
          idMode = modeMapping.get(dummy.valueId);
          dummy.setOperationalMode(dummy.valueId, idMode);
          System.err.println(msg + arg[1]);
          break;


        // update time
        case "update":
          try {
            Integer value = Integer.parseInt(arg[1]);
            dummy.updateTime = value;

            // restart loop
            dummy.loopFuture.cancel(true);
            dummy.startLoop();

            System.err.println(">>update time changed to: " + value + "ms");
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
                    dummy.value = valueMapping.get(dummy.valueId);
                    System.err.println(">>ID changed to: " + values[i].getId());
                    found=true;
                    break;
                }
              }
              if (!found){
                  System.err.println(">>ID: " + arg[1] + " does not exist.");
              }
              break;


          // print out current ID
          case "current":
              System.err.println(">>The current ID is: " + dummy.valueId);
              break;


          // print out instructions
          case "?":
              instructions(values);
              break;


          // change all IDs to the same value
          case "allvalue":
              Double value = Double.parseDouble(arg[1]);
              for (Value i : values ){
                  valueMapping.put(i.getId(), value);
              }
              System.err.println(">>Changed all IDs to value: " + arg[1]);
              break;


          // change value by specifying the ID
          case "change":
              found=false;
              if (arg[3].equals("value")) {
                  Double Value = Double.parseDouble(arg[4]);
                  for (int i = 0; i < values.length; i = i + 1) {
                      if (values[i].getId().equals(arg[1])) {
                          valueMapping.put(values[i].getId(), Value);
                          System.err.println(">>value in " + arg[1] + " has been changed to the value: " + arg[4]);
                          found = true;
                          break;
                      }
                  }
                  if (!found) {
                      System.err.println(">>ID: " + arg[1] + " does not exist.");
                  }


              } else if (arg[3].equals("mode")) {
                  String modemsg = ">>" + arg[1] + " has been changed to operational mode: ";
                      for (int i = 0; i < values.length; i = i + 1) {
                          if (values[i].getId().equals(arg[1])) {
                              modeMapping.put(values[i].getId(), OperationalMode.valueOf(arg[4].toUpperCase()));
                              idMode = modeMapping.get(values[i].getId());
                              dummy.setOperationalMode(values[i].getId(), idMode);
                              System.err.println(modemsg + arg[4]);
                              found = true;
                              break;
                          }
                      }
                  if (!found) {
                      System.err.println(">>ID: " + arg[1] + " does not exist.");
                  }
              } else if (arg[3].equals("UpdateTime")) {
                  System.err.println(arg[4]);
              } else {
                  System.err.println(">>unrecognized command: " + line);
              }
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

  private MultiDummyPlugin(PluginConfig config, MonitorPointSender sender, HashMap<String, Double> hmConstructror) {
    super(config, sender, new HbKafkaProducer(
			config.getId(), config.getSinkServer() + ":" + config.getSinkPort(),
			new HbJsonSerializer())
		);
    hm = hmConstructror;
  }

  /**
   * Override method to catch the exception and log a message
   * <p>
   * In the example we do not take any special action if the Plugin returns an
   * error when submitting a new value.
   */
  @Override
  public void updateMonitorPointValue(String mPointID, Object value) {
     Set<String> keys = hm.keySet();
    try {
        for (String key: keys){
            super.updateMonitorPointValue(key, hm.get(key));
        }


      //super.updateMonitorPointValue(mPointID, value);
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
