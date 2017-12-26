package org.eso.ias.plugin.test.weather;

import javax.xml.soap.*;

public class SOAPRequest {

	// SAAJ - SOAP Client Testing
	public static void main(String args[]) {
		String endpointUrl = "http://weather.aiv.alma.cl/ws_weather.php";
		String target = "http://weather.aiv.alma.cl/ws_weather.php";
		String action = "getCurrentWeatherData";
		String idName = "id";

		SOAPRequest soap = new SOAPRequest(endpointUrl, target, action, idName);

		for (int i = 2; i < 12; i++) {
			String response = soap.sendRequest(Integer.toString(i));
			System.out.println(response);
		}
	}

	// request parameters
	/**
	 * endpoint to make the request.
	 */
	private String endpointUrl;

	/**
	 * target of the action, might be the equal to the endpoint.
	 */
	private String target;

	/**
	 * action of the request.
	 */
	private String action;

	/**
	 * name of the single parameter for the request.
	 */
	private String idName;

	// SOAP elements
	private SOAPConnection connection;
	private SOAPMessage message;
	private SOAPElement text;

	/**
	 * Creates a SOAPRequest that can send requests to the endpoint specified,
	 * providing the target, action and name of the id given to the service.
	 * 
	 * @param endpointUrl
	 * @param target
	 * @param action
	 * @param idName
	 */
	public SOAPRequest(String endpointUrl, String target, String action, String idName) {
		this.endpointUrl = endpointUrl;
		this.target = target;
		this.action = action;
		this.idName = idName;

		createConnection();
		createMessage();
	}

	/**
	 * readies the connection and the message to send request to the web service.
	 */
	private void createConnection() {
		try {
			// Create SOAP Connection
			SOAPConnectionFactory connectionFactory = SOAPConnectionFactory.newInstance();
			connection = connectionFactory.createConnection();
		} catch (Exception e) {
			System.err.println("Error occurred while creating connection.");
			System.err.println(e.getMessage() + e.getStackTrace());
		}
	}

	/**
	 * creates the soap message object with all the info required for the request,
	 * except for the value of the id.
	 * 
	 * @throws SOAPException
	 */
	private void createMessage() {
		try {
			MessageFactory messageFactory = MessageFactory.newInstance();
			message = messageFactory.createMessage();
			SOAPPart part = message.getSOAPPart();

			String ns = "ns";

			// SOAP Envelope
			SOAPEnvelope envelope = part.getEnvelope();
			envelope.addNamespaceDeclaration(ns, target);
			envelope.addNamespaceDeclaration("xsi", "http://www.w3.org/2001/XMLSchema-instance");
			envelope.addNamespaceDeclaration("ns3", "http://www.w3.org/2001/XMLSchema");

			// SOAP Body
			SOAPBody body = envelope.getBody();
			SOAPElement elem = body.addChildElement(action, ns);

			// where to add the value
			SOAPElement elemInfo = elem.addChildElement(idName);
			elemInfo.setAttribute("xsi:type", "ns3:integer");
			text = elemInfo.addTextNode("");

			// header
			MimeHeaders headers = message.getMimeHeaders();
			headers.addHeader("SOAPAction", target + "/" + action);

			message.saveChanges();

		} catch (Exception e) {
			System.err.println("Error occurred while creating message.");
			System.err.println(e.getMessage() + e.getStackTrace());
		}
	}

	/**
	 * changes the value of the id in the message created for the request.
	 * 
	 * @param idValue
	 *            the value to be used in the request.
	 * @throws Exception
	 */
	private void setRequestValue(String idValue) {
		try {
			text.setTextContent(idValue);
			message.saveChanges();
		} catch (Exception e) {
			System.err.println("Error while setting request value.");
			System.err.println(e.getMessage() + e.getStackTrace());
		}
	}

	/**
	 * Sends a request using the message and connection from the class, changes the
	 * value of the message to the given parameter.
	 * 
	 * @param idValue
	 * @return the text of the response, if there's more than one node with text
	 *         it's concatenated.
	 */
	public String sendRequest(String idValue) {
		// set value for request
		setRequestValue(idValue);

		try {
			// send request and get response
			SOAPMessage soapResponse = connection.call(message, endpointUrl);
			String body = soapResponse.getSOAPPart().getEnvelope().getTextContent();

			return body;

		} catch (Exception e) {
			System.err.println("Error while making the call");
			e.printStackTrace();
		}

		return null;
	}
}