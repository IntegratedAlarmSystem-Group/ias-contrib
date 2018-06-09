public class WeatherStationExample {

  public static void main(String[] args) {
    int stationId = 2;
    int stationTTL = 2000;

		WeatherStation station = new WeatherStation(stationId, stationTTL);
		station.updateValues();
		System.out.println(station);
	}

}
