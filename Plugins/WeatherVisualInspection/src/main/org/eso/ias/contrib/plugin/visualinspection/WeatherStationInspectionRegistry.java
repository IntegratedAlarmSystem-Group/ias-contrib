package org.eso.ias.contrib.plugin.visualinspection;

/**
 * The last visual inspection registry, to be read by the plugin
 *
 * @author sfehlandt
 *
 */
public class WeatherStationInspectionRegistry {

	/**
	 * The station of the weather station
	 */
	private String station;

	/**
	* ISO 8601 time stamp of the last inspection
	*/
	private String timestamp;

	/**
	* Name of the user who did the inspection registry
	*/
	private String username;

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
			String timestamp,
			String username
			) {
		if (station==null || station.isEmpty()) {
			throw new IllegalArgumentException("Invalid null or empty station");
		}
		this.station = station;
		if (timestamp==null || timestamp.isEmpty()) {
			throw new IllegalArgumentException("Invalid null or empty timestamp");
		}
		this.timestamp = timestamp;
		if (username!=null && !username.isEmpty()) {
			this.username = username;
		}
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
	 * @return the username
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * Setter
	 *
	 * @param value the username
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * Default String representation
	 */
	@Override
	public String toString() {
		return "Station " + this.getStation() + " last inspected at " +
		this.getTimestamp() + " by user " + this.getUsername();
	}

}
