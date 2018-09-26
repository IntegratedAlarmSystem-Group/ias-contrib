package org.eso.ias.contrib.plugin.visualinspection;

/**
 * The last visual inspection registry, to be read by the plugin
 *
 * @author sfehlandt
 *
 */
public class WeatherStationInspectionRegistry {

	/**
	 * ISO 8601 time stamp of the last inspection
	 */
	private String timestamp;

	/**
	 * The station of the weather station
	 */
	private String station;

	/**
	 * Empty constructor
	 */
	public WeatherStationInspectionRegistry() {}

	/**
	 *
	 * Constructor
	 *
	 * @param station the station of the weather station
	 * @param timestamp ISO 8601 time stamp
	 */
	public WeatherStationInspectionRegistry(
			String station,
			String timestamp
			) {
		if (station==null || station.isEmpty()) {
			throw new IllegalArgumentException("Invalid null or empty station");
		}
		this.station = station;
		if (timestamp==null || timestamp.isEmpty()) {
			throw new IllegalArgumentException("Invalid null or empty timestamp");
		}
		this.timestamp = timestamp;
	}

	/**
	 * Getter
	 *
	 * @return the timestamp
	 */
	public String getTimestamp() {
		return timestamp;
	}

	/**
	 * Setter
	 *
	 * @param timestamp the timestamp
	 */
	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}

	/**
	 * Getter
	 *
	 * @return the station
	 */
	public String getStation() {
		return station;
	}

	/**
	 * Setter
	 *
	 * @param value the station
	 */
	public void setStation(String station) {
		this.station = station;
	}

  public String toString() {
    return "Station: " + this.getStation() + ", Last inspection: " + this.getTimestamp();
  }

}
