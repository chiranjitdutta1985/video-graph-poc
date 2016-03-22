package com.timeinc.assetmgr.video;


import java.io.DataOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;

/**
 * 
 * @author Lokesh.Gami
 *
 *         Mar 8, 2016 : 5:08:30 PM
 */
public class VideoMetaDataProvider {

	private static final Logger logger = Logger.getLogger(VideoMetaDataProvider.class);

	private String accountId = "2111767321001";
	private String brightCoveUrl = "https://cms.api.brightcove.com/v1/accounts/%s/videos?limit=100&offset=%d";
	private String brightCoveAPIUrl = "https://oauth.brightcove.com/v3/access_token";
	private String brightCoveAPIKey = "fc9325e2-aabb-4ae9-a1d1-0fa903ea48dd";
	private String brightCoveAPISecret = "UzHEUJbHicwJa3F-KaLgt2lMKCKIWzzNf26LJaRGPgSjHZNMKEIQ2brjdSGWAZrr_cZgWUO0TeCoKR6pxQPrOw";
	private String brightCoveApiCountUrl = "https://cms.api.brightcove.com/v1/accounts/%s/counts/videos";
	//	private String rampDataServiceUrl="http://dataservices.ramp.com/mediacloudapi/%s/?syndication=dataservices&guid=%s&xsl=rawtext.xsl&RampAPIUser=%s&RampAPIPassword=%s";
	//	private String brandName="fortune";
	//	private String rampAPIUser="ashis.roy@timeinc.com";
	//	private String rampAPIPassword="Time2016!";
	private String assetTaggingApiEndPoint="http://videosuggestdevenv-env.us-west-2.elasticbeanstalk.com/v1/asset-info";
	private String authorizationKey="FE66489F304DC75B8D6E8200DFF8A456E8DAEACEC428B427E9518741C92C6660";
	private String transcriptUrl = "https://cms.api.brightcove.com/v1/accounts/2111767321001/videos/";

	private HttpURLConnection httpConnection;
	private URL url;
	private ObjectMapper mapper = new ObjectMapper();
	private JsonNode jsonNode ;
	private Map<String, JsonNode> allVideo =  new HashMap<String, JsonNode>();;
	private int offset = 0;
	private Map<String, String> entitiesWithType = new HashMap<String, String>();


	static {
		try {
			SSLDisabledUtil.disableSslVerification();
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	public static void main(String args[]) throws Throwable{
		try 
		{

			long start = System.currentTimeMillis();
			VideoMetaDataProvider object =  new VideoMetaDataProvider();
			//			object.dummy();
			Map<String, JsonNode> brightCoveResponse = (Map<String, JsonNode>) object.readVideo();
			Set<String> indexIds = Sets.newHashSet();

			for (String key : brightCoveResponse.keySet()) {
				System.out.println(key);
				indexIds.add(key);
			}

			object.loadAndProcessTranscripts(brightCoveResponse, indexIds);
			long end = System.currentTimeMillis();
			logger.info("Time Taken = :"+(end-start)/1000);
			System.out.println("Time Taken = :"+(end-start)/1000);

		} catch (Throwable th) {
			//logger.error("Error", th);
			System.out.println("Error" + th);
			throw th;
		}

	}

	private Map<String, JsonNode> readVideo() throws Exception{
		try {

			String accessToken = getAccessToken();

			//logger.info("Access Token : " + accessToken);
			System.out.println("Access Token : "+ accessToken);

			Preconditions.checkArgument(!Strings.isNullOrEmpty(accountId));
			int videoCount = getVideoCountFromApi(accessToken, accountId);

			logger.info("Total no. of videos present in Brightcove: " + videoCount);
			System.out.println("Total no. of videos present in Brightcove: " + videoCount);

			if (videoCount > offset) {
				int iterateLoop = (videoCount - offset) / 100;
				int iterateRemain = (videoCount - offset) % 100;

				if (iterateRemain > 0 && iterateRemain < 100) {
					iterateLoop++;
				}

				for (int i = 1; i <= iterateLoop; i++) {
					logger.info("Current offset at: " + offset);
					System.out.println("Current offset at: " + offset);

					fetchBrightCoveRecords(offset, accessToken, accountId);

					if (i < iterateLoop || iterateRemain == 0)
						offset += 100;
					else
						offset += iterateRemain;

				}
			} else {
				logger.info("Video count in ES is more or equal to BrightCove.");
				System.out.println("Video count in ES is more or equal to BrightCove.");
			}

			logger.info("Final offset at: " + offset);
			System.out.println("Final offset at: " + offset);
		} catch (Exception e) {
			throw new Exception("Exception in accessing Brightcove API. ", e);
		}

		return allVideo;
	}
	/**
	 * 
	 * @param jsonNode
	 * @param transcript
	 * @return Document ready
	 * @throws Exception
	 */
	private JSONObject getTagsFromListedProviders(JsonNode jsonNode, StringBuilder transcript) throws Exception {

		this.validateRequest(jsonNode, transcript);

		logger.info("Transcript received from RAMP MediaCloud API : " + transcript);
		System.out.println("Transcript received from RAMP MediaCloud API : " + transcript);

		ObjectMapper mapper = new ObjectMapper();

		String videoDescription = String.format("%s. %s. %s", jsonNode.path("name").asText(),
				jsonNode.path("description").asText(), transcript);

		String resultFromProvider = getTagsFromProviders(videoDescription);

		String json = resultFromProvider.substring(resultFromProvider.indexOf("(") + 1, resultFromProvider.lastIndexOf(")"));

		String captionAndDescription = String.format("%s. %s", jsonNode.path("name").asText(),
				jsonNode.path("description").asText());

		JsonNode result = mapper.readTree(json);

		Preconditions.checkArgument(null != result && null != result.get("data"));

		JsonNode dataNode = result.path("data");

		String resultFromProvider2 = getTagsFromProviders(captionAndDescription);

		String json2 = resultFromProvider.substring(resultFromProvider2.indexOf("(") + 1, resultFromProvider2.lastIndexOf(")"));

		JsonNode result2 = mapper.readTree(json2);

		Preconditions.checkArgument(null != result2 && null != result2.get("data"));

		JsonNode dataNode2 = result2.path("data");

		logger.info("data :" + dataNode + "data 2 :"+dataNode2);
		System.out.println("data :" + dataNode);

		Map<String, Double> keywordsWithRelevenace = new HashMap<String, Double>();
		Map<String, Double> entitiesWithRelevenace = new HashMap<String, Double>();
		Map<String, Double> taxonomyWithRelevenace = new HashMap<String, Double>();
		Map<String, Double> sentimentsWithRelevenace = new HashMap<String, Double>();

		//tagsWithRelevenace = formatTags(tagsWithRelevenace, dataNode);

		keywordsWithRelevenace = formatKeywords(keywordsWithRelevenace, dataNode);
		entitiesWithRelevenace = formatEntities(entitiesWithRelevenace, dataNode);
		taxonomyWithRelevenace = formatTaxonomy(taxonomyWithRelevenace, dataNode);
		sentimentsWithRelevenace = formatSentiments(sentimentsWithRelevenace, dataNode);

		keywordsWithRelevenace = formatKeywords(keywordsWithRelevenace, dataNode2);
		entitiesWithRelevenace = formatEntities(entitiesWithRelevenace, dataNode2);
		taxonomyWithRelevenace = formatTaxonomy(taxonomyWithRelevenace, dataNode2);
		sentimentsWithRelevenace = formatSentiments(sentimentsWithRelevenace, dataNode2);

		Preconditions.checkArgument(null != jsonNode.get("tags"));

		ArrayNode tags = (ArrayNode) jsonNode.path("tags");

		JSONArray keywordArray = new JSONArray();

		for (JsonNode existTag : tags) {

			Preconditions.checkArgument(null != existTag.path("tag"));
			String tagInES = existTag.path("tag").asText();

			JSONObject tagObject = new JSONObject();
			tagObject.put("text", tagInES);
			tagObject.put("relevance", "0.99");
			keywordArray.put(tagObject);

			if (keywordsWithRelevenace.containsKey(tagInES))
				keywordsWithRelevenace.remove(tagInES);

			if (entitiesWithRelevenace.containsKey(tagInES))
				entitiesWithRelevenace.remove(tagInES);

			if (taxonomyWithRelevenace.containsKey(tagInES))
				taxonomyWithRelevenace.remove(tagInES);
		}

		for (String tag : keywordsWithRelevenace.keySet()) {
			JSONObject keyword = new JSONObject();

			keyword.put("text", tag);
			keyword.put("relevance", keywordsWithRelevenace.get(tag).toString());

			keywordArray.put(keyword);
		}

		JSONObject output = new JSONObject();
		output.put("keywords", keywordArray);

		JSONArray entitiesArray = new JSONArray();

		for (String tag : entitiesWithRelevenace.keySet()) {

			JSONObject entities = new JSONObject();

			entities.put("text", tag);
			entities.put("relevance", entitiesWithRelevenace.get(tag).toString());
			entities.put("type", entitiesWithType.get(tag).toString());

			entitiesArray.put(entities);
		}

		output.put("entities", entitiesArray);

		JSONArray sentimentsArray = new JSONArray();

		for (String tag : sentimentsWithRelevenace.keySet()) {
			JSONObject sentiment = new JSONObject();

			sentiment.put("text", tag);
			sentiment.put("relevance", sentimentsWithRelevenace.get(tag).toString());

			sentimentsArray.put(sentiment);
		}
		output.put("sentiments", sentimentsArray);

		JSONArray taxonomyArray = new JSONArray();

		for (String tag : taxonomyWithRelevenace.keySet()) {

			JSONObject taxonomy = new JSONObject();
			taxonomy.put("tag", tag);
			taxonomy.put("relevance", taxonomyWithRelevenace.get(tag).toString());

			taxonomyArray.put(taxonomy);
		}

		output.put("taxonomy", taxonomyArray);

		return output;
	}

	/**
	 * 
	 * @param videoDescription
	 * @return API response
	 * @throws Exception
	 */
	private String getTagsFromProviders(String videoDescription) throws Exception {
		String resValue = null;

		JSONObject requestJson = new JSONObject();
		requestJson.put("assetType", "TEXT");
		requestJson.put("text", videoDescription);
		String[] requiredInfoFields = { "ALL" };
		requestJson.put("requiredInfoFields", requiredInfoFields);

		IHttpClient client = new THttpClient(assetTaggingApiEndPoint);

		client.addRequestProperty("Authorization", authorizationKey);

		resValue = client.post(requestJson).toString();
		//logger.info("Result: " + resValue);
		System.out.println("Result: " + resValue);

		return resValue;
	}


	/**
	 * 
	 * @param jsonNode
	 * @param transcript
	 */

	private void validateRequest(JsonNode jsonNode, StringBuilder transcript){
		Preconditions.checkArgument(null != jsonNode);
		Preconditions.checkArgument(null != jsonNode.get("name") || null != jsonNode.get("description") || null != transcript);
	}

	/**
	 * 
	 * @param tagsWithRelevenace
	 * @param dataNode
	 * @return formatted tags
	 */
	private Map<String, Double> formatTags(Map<String, Double> tagsWithRelevenace, JsonNode dataNode) {

		Preconditions.checkArgument(null != dataNode.get("keywords"));
		JsonNode keywords = dataNode.path("keywords");

		for (JsonNode keyword : keywords) {
			putTagsInMap(tagsWithRelevenace, keyword);
		}

		Preconditions.checkArgument(null != dataNode.get("entities"));
		JsonNode entities = dataNode.path("entities");

		for (JsonNode entity : entities) {
			putTagsInMap(tagsWithRelevenace, entity);
		}

		Preconditions.checkArgument(null != dataNode.get("taxonomy"));
		JsonNode taxonomies = dataNode.path("taxonomy");

		for (JsonNode taxonomy : taxonomies) {
			putTagsInMap(tagsWithRelevenace, taxonomy);
		}

		return tagsWithRelevenace;
	}

	private Map<String, Double> formatKeywords(Map<String, Double> tagsWithRelevenace, JsonNode dataNode) {

		Preconditions.checkArgument(null != dataNode.get("keywords"));
		JsonNode keywords = dataNode.path("keywords");

		for (JsonNode keyword : keywords) {
			putTagsInMap(tagsWithRelevenace, keyword);
		}

		return tagsWithRelevenace;
	}

	private Map<String, Double> formatEntities(Map<String, Double> tagsWithRelevenace, JsonNode dataNode) {


		Preconditions.checkArgument(null != dataNode.get("entities"));
		JsonNode entities = dataNode.path("entities");

		for (JsonNode entity : entities) {
			putTagsInMap(tagsWithRelevenace, entity);
		}

		return tagsWithRelevenace;
	}

	private Map<String, Double> formatTaxonomy(Map<String, Double> tagsWithRelevenace, JsonNode dataNode) {


		Preconditions.checkArgument(null != dataNode.get("taxonomy"));
		JsonNode taxonomies = dataNode.path("taxonomy");

		for (JsonNode taxonomy : taxonomies) {
			putTagsInMap(tagsWithRelevenace, taxonomy);
		}

		return tagsWithRelevenace;
	}


	private Map<String, Double> formatSentiments(Map<String, Double> tagsWithRelevenace, JsonNode dataNode) {


		Preconditions.checkArgument(null != dataNode.get("sentiments"));
		JsonNode sentiments = dataNode.path("sentiments");

		for (JsonNode sentiment : sentiments) {
			putTagsInMap(tagsWithRelevenace, sentiment);
		}

		return tagsWithRelevenace;
	}

	/**
	 * 
	 * @param tagsWithRelevenace
	 * @param infoNode
	 */
	private void putTagsInMap(Map<String, Double> tagsWithRelevenace, JsonNode infoNode) {

		String text = null;
		Double relevance = 0.60;
		String type = "";

		if (null != infoNode.get("text") && null != infoNode.get("relevance")) {
			text = infoNode.path("text").asText().toLowerCase();
			relevance = new Double(infoNode.path("relevance").asDouble());

			if(infoNode.get("type") != null){
				type = infoNode.path("type").asText().toLowerCase();
			}

		} else if (null != infoNode.get("label") && null != infoNode.get("score")) {
			text = infoNode.path("label").asText().toLowerCase();
			relevance = new Double(infoNode.path("score").asDouble());
		}else if (null != infoNode.get("score") && null != infoNode.get("type")) {
			text = infoNode.path("type").asText().toLowerCase();
			relevance = new Double(infoNode.path("score").asDouble());
		}

		DecimalFormat decimalFormat = new DecimalFormat("#.##");
		relevance = Double.valueOf(decimalFormat.format(relevance));

		if (tagsWithRelevenace.containsKey(text)) {
			Double relevanceIn = tagsWithRelevenace.get(text);

			if (relevance > relevanceIn)
				tagsWithRelevenace.put(text, relevance);
		} else if (relevance > Double.valueOf(0.60)) {
			tagsWithRelevenace.put(text, relevance);
		}

		if(!Strings.isNullOrEmpty(type)){
			entitiesWithType.put(text, type);
		}

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

	private int getVideoCountFromApi(String accessToken, String accountId) throws Exception {
		String brightCoveUrlCount = String.format(this.brightCoveApiCountUrl, accountId);

		url = new URL(brightCoveUrlCount);
		String authType = String.format("%s %s", "Bearer", accessToken);

		httpConnection = getApiHttpConnection(url, "GET", authType);

		int responseCode = httpConnection.getResponseCode();
		//		logger.info("\nSending 'GET' request to URL : " + brightCoveUrlCount);
		//		logger.info("Response Code : " + responseCode);

		System.out.println("\nSending 'GET' request to URL : " + brightCoveUrlCount);
		System.out.println("Response Code : " + responseCode);
		jsonNode = mapper.readTree(httpConnection.getInputStream());

		Preconditions.checkArgument(null != jsonNode && null != jsonNode.get("count"));

		return jsonNode.get("count").asInt();
	}

	private void fetchBrightCoveRecords(int offset, String... params) throws Exception {
		String brightCoveUrl = String.format(this.brightCoveUrl, params[1], offset);

		JsonNode apiResponse = getBrightCodeData(brightCoveUrl, params[0]);
		String keyStore;

		for (JsonNode responseNode : apiResponse) {
			Preconditions.checkArgument(responseNode != null);
			Preconditions.checkArgument(null != responseNode.get("account_id") && null != responseNode.get("id"));
			keyStore = String.format("%s_%s", responseNode.get("account_id").asText(), responseNode.get("id").asText());
			allVideo.put(keyStore, responseNode);
		}
	}

	private HttpURLConnection getApiHttpConnection(URL url, String requestMethod, String authType) throws IOException {

		httpConnection = (HttpURLConnection) url.openConnection();
		httpConnection.setRequestMethod(requestMethod);
		httpConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		httpConnection.setRequestProperty("Authorization", authType);
		httpConnection.setDoOutput(true);

		return httpConnection;
	}

	private JsonNode getBrightCodeData(String brightCoveUrl, String accessToken) throws IOException {
		url = new URL(brightCoveUrl);
		String authType = String.format("%s %s", "Bearer", accessToken);

		httpConnection = getApiHttpConnection(url, "GET", authType);

		int responseCode = httpConnection.getResponseCode();
		//		logger.info("\nSending 'GET' request to URL : " + brightCoveUrl);
		//		logger.info("Response Code : " + responseCode);

		System.out.println("\nSending 'GET' request to URL : " + brightCoveUrl);
		System.out.println("Response Code : " + responseCode);

		return mapper.readTree(httpConnection.getInputStream());
	}


	/**
	 * 
	 * @param indexIds
	 * @param params
	 * @throws Throwable
	 */

	private void loadAndProcessTranscripts(Map<String, JsonNode> brightCoveResponse, Set<String> indexIds) throws Throwable{// Load it back to retrieve tag details.
		VideoMetaDataProvider object = new VideoMetaDataProvider();
		PrintWriter out = new PrintWriter(new FileWriter("logs/video_logs/log.json"));
		int count = 0;
		int i = 1;
		out.append("[");

		for (String id : indexIds) {
			try{
				logger.info("###############STARTING FOR ID : "+id);
				count ++;
				JsonNode nodeResult = (JsonNode) brightCoveResponse.get(id);

				StringBuilder transcript = (StringBuilder) object.getTranscript(nodeResult.get("id").asText().trim());
				logger.info(nodeResult.get("id").asText().trim() + " " + transcript);

				// Make a call to the Provider API to get tags.
				JSONObject response = (JSONObject) getTagsFromListedProviders(nodeResult, transcript);

				response.put("assetId", nodeResult.get("id").asText());

				response.put("assetType", "VIDEO");

				response.put("brandId", "FORTUNE");


				if(nodeResult.has("description")){
					response.put("description", nodeResult.get("description").asText());
				}

				if(nodeResult.has("geo")){
					response.put("geo", nodeResult.get("geo").asText());
				}

				if(nodeResult.has("name")){
					response.put("caption", nodeResult.get("name").asText());
				}

				if(nodeResult.has("duration")){
					response.put("duration", nodeResult.get("duration").asText());
				}

				if(nodeResult.has("state")){
					response.put("state", nodeResult.get("state").asText());
				}

				if(nodeResult.has("updated_at")){
					response.put("lastModified", nodeResult.get("updated_at").asText());
				}

				if(nodeResult.has("images")){
					ObjectNode images = (ObjectNode) nodeResult.path("images");

					String url = "";
					for (JsonNode node : images) {
						if(node.has("src")){
							url = node.path("src").asText();
							break;
						}
					}

					response.put("thumbnailUrl", url);
				}

				//	logger.info("Index with id : " + id + " has updated tags.");
				System.out.println("Index with id : " + id + " has updated tags.");

				System.out.println(response.toString());



				//				finalResult = nodeResult.get("id");
				//				finalResult = nodeResult.get("account_id");
				//				finalResult = nodeResult.get("tags");

				out.append(response.toString());

				//			logger.info(nodeResult.toString());
				//			logger.info("\n");
				if(count == 5){
					break;
				}

				if(count>=i*100){
					count = 0;
					out.append("]");
					out.close();
					out = null ;
					out = new PrintWriter(new FileWriter("logs/video_logs/log"+i+".json"));
					out.append("[");
					i++;
				}else{
					out.append(",");
				}

				logger.info("###############SUCCESS FOR ID : "+id);
				//				break;
			}catch(Exception e){
				logger.info("###############EXCEPTION FOR ID : "+id);
				continue;
			}
		}
		out.append("]");
		out.close();
		out = null ;
	}

	//	public StringBuilder getTranscript(String id)
	//			throws Exception {
	//
	//		StringBuilder transcript = new StringBuilder();
	//
	//		String guid = id.split("_")[1];
	//
	//		//		logger.info("GUID received is " + guid);
	//		//		logger.info("\n");
	//		System.out.println("GUID received is " + guid);
	//
	//
	//		String rampDataServiceUrl = String.format(this.rampDataServiceUrl, this.brandName, guid, this.rampAPIUser, this.rampAPIPassword);
	//		this.url = new URL(rampDataServiceUrl);
	//
	//		httpConnection = getApiHttpConnection(this.url, "GET", null);
	//
	//
	//		//		BufferedReader reader = new BufferedReader(new InputStreamReader(httpConnection.getInputStream()));
	//		//
	//		//		String stringText;
	//		//		while ((stringText = reader.readLine()) != null) {
	//		//			transcript.append(stringText);
	//		//		}
	//
	//		String text = "Every year, OFA's top volunteers from around the country gather in Chicago at the annual Volunteer Leadership Summit to share their experiences from the previous year and apply the things they've learned to their plans for the year ahead."
	//				+"Kicking off OFA's 2016 training calendar, 77 passionate volunteer leaders from 22 states joined together to learn from each other and some of the top organizing experts."
	//				+"Check out what happened at the summit and find out more about the power of training.";
	//		transcript.append(text);
	//		return transcript;
	//
	//	}

	private StringBuilder getTranscript(String id) throws Exception{

		try{
			String accessToken = getAccessToken();

			String transcriptServiceUrl = transcriptUrl + id + "/assets/caption";

			url = new URL(transcriptServiceUrl);
			String authType = String.format("%s %s", "Bearer", accessToken);

			httpConnection = getApiHttpConnection(url, "GET", authType);

			jsonNode = mapper.readTree(httpConnection.getInputStream());

			String xml = jsonNode.get("remote_url").asText();
			ExtractTranscript obj = new ExtractTranscript();
			StringBuilder transcript = obj.extractTranscript(xml);
			return transcript;
		}catch(Exception e){
			return new StringBuilder();
		}

	}

	//	private void dummy() throws IOException{
	//		int count = 0;
	//		PrintWriter out = new PrintWriter(new FileWriter("/Users/bhandarr/Documents/video_logs/app.txt"));
	//		try{
	//			for(int i=0 ; i<45; i++){
	//				count++;
	//				out.append("hi"+i);
	//				out.append("\n");
	//				if(count>=40){
	//					count = 0;
	//					out.close();
	//					out = new PrintWriter(new FileWriter("/Users/bhandarr/Documents/video_logs/app"+i+".txt"));
	//				}
	//			}
	//		}catch(Exception e){
	//			System.out.println(e);
	//		}finally{
	//			out.close();
	//		}
	//	}

}

