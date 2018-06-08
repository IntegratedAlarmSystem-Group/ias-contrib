public class WeatherStationExample {

  public static void main(String[] args) {
    int sensorId = 2;
    int sensorTTL = 2000;

		WeatherSensor sensor = new WeatherSensor(sensorId, sensorTTL);
		sensor.updateValues();
		System.out.println(sensor);
	}

}
