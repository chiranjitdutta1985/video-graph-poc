package com.timeinc.assetmgr.video;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.codec.binary.Base64;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;

public class ExtractTranscript {

	private HttpURLConnection httpConnection;
	private URL url;

	//private String brightCoveUrl = "https://cms.api.brightcove.com/v1/accounts/2111767321001/videos/";
	private ObjectMapper mapper = new ObjectMapper();
	private JsonNode jsonNode ;
	private String brightCoveUrl = "https://cms.api.brightcove.com/v1/accounts/2111767321001/videos/";
	private String brightCoveAPIUrl = "https://oauth.brightcove.com/v3/access_token";
	private String brightCoveAPIKey = "fc9325e2-aabb-4ae9-a1d1-0fa903ea48dd";
	private String brightCoveAPISecret = "UzHEUJbHicwJa3F-KaLgt2lMKCKIWzzNf26LJaRGPgSjHZNMKEIQ2brjdSGWAZrr_cZgWUO0TeCoKR6pxQPrOw";


	public static void main(String args[]) throws Exception{
		ExtractTranscript obj = new ExtractTranscript();
		StringBuilder res = obj.getTranscript("3599304814001");
		System.out.println();
	}

	public StringBuilder getTranscript(String id) throws Exception{

		String accessToken = getAccessToken();
		String transcriptServiceUrl = brightCoveUrl + id + "/assets/caption";

		url = new URL("https://cms.api.brightcove.com/v1/accounts/2111767321001/videos/4271330769001/assets/caption");
		String authType = String.format("%s %s", "Bearer", accessToken);

		httpConnection = getApiHttpConnection(url, "GET", authType);

		jsonNode = mapper.readTree(httpConnection.getInputStream());

		String xml = jsonNode.get("remote_url").asText();

		StringBuilder transcript = extractTranscript(xml);
		return transcript;

	}

	public StringBuilder extractTranscript(String xml) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException{


		URL url = new URL(xml);
		URLConnection urlConnection = url.openConnection();
		InputStream in = new BufferedInputStream(urlConnection.getInputStream());

		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		StringBuilder out = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			out.append(line);
		}

		Document doc = convertStringToDocument(out.toString());
		doc.getDocumentElement().normalize();

		NodeList list = doc.getElementsByTagName("body");

		StringBuilder transcript = new StringBuilder();
		for (int temp = 0; temp < list.getLength(); temp++) 
		{
			Node node = list.item(temp);

			NodeList child = node.getChildNodes();

			for (int temp2 = 0; temp2 < child.getLength(); temp2++) {
				Node node2 = child.item(temp2);
				NodeList child2 = node2.getChildNodes();

				for(int temp3 = 0; temp3 < child2.getLength(); temp3++){
					Node node3 = child2.item(temp3);
					transcript.append(node3.getTextContent());
				}
			}

		}

		return transcript;
	}

	//	private  String convertDocumentToString(Document doc) {
	//		TransformerFactory tf = TransformerFactory.newInstance();
	//		Transformer transformer;
	//		try {
	//			transformer = tf.newTransformer();
	//			// below code to remove XML declaration
	//			// transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
	//			StringWriter writer = new StringWriter();
	//			transformer.transform(new DOMSource(doc), new StreamResult(writer));
	//			String output = writer.getBuffer().toString();
	//			return output;
	//		} catch (TransformerException e) {
	//			e.printStackTrace();
	//		}
	//
	//		return null;
	//	}

	private Document convertStringToDocument(String xmlStr) {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();  
		DocumentBuilder builder;  
		try 
		{  
			builder = factory.newDocumentBuilder();  
			Document doc = builder.parse( new InputSource( new StringReader( xmlStr ) ) ); 
			return doc;
		} catch (Exception e) {  
			e.printStackTrace();  
		} 
		return null;
	}

	private String getAccessToken() throws Exception {
		url = new URL(brightCoveAPIUrl);
		String authStr;
		Preconditions.checkArgument(null != brightCoveAPIKey && null != brightCoveAPISecret);
		authStr = String.format("%s:%s", brightCoveAPIKey, brightCoveAPISecret);

		authStr = Base64.encodeBase64String(authStr.getBytes());

		authStr = String.format("%s %s", "Basic", authStr);

		httpConnection = getApiHttpConnection(url, "POST", authStr);

		String urlParameters = "grant_type=client_credentials";
		DataOutputStream requestParams = new DataOutputStream(httpConnection.getOutputStream());
		requestParams.writeBytes(urlParameters);
		requestParams.flush();
		requestParams.close();

		int responseCode = httpConnection.getResponseCode();
		//		logger.info("\nSending 'POST' request to URL : " + this.brightCoveAPIUrl);
		//		logger.info("Post parameters : " + urlParameters);
		//		logger.info("Response Code : " + responseCode);

		System.out.println("\nSending 'POST' request to URL : " + this.brightCoveAPIUrl);
		System.out.println("Post parameters : " + urlParameters);
		System.out.println("Response Code : " + responseCode);

		jsonNode = mapper.readTree(new InputStreamReader(httpConnection.getInputStream()));

		Preconditions.checkArgument(null != jsonNode && null != jsonNode.get("access_token"));

		//logger.info("Access token node : " + jsonNode.path("access_token"));
		System.out.println("Access token node : " + jsonNode.path("access_token"));

		return jsonNode.path("access_token").asText();
	}
	private HttpURLConnection getApiHttpConnection(URL url, String requestMethod, String authType) throws IOException {

		httpConnection = (HttpURLConnection) url.openConnection();
		httpConnection.setRequestMethod(requestMethod);
		httpConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		httpConnection.setRequestProperty("Authorization", authType);
		httpConnection.setDoOutput(true);

		return httpConnection;
	}
}
