package com.timeinc.assetmgr.video;

/**
 * 
 * @author Ritu-Bhandari
 *
 */

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONObject;

public class THttpClient implements IHttpClient {

	private HttpURLConnection connection;

	private URL url;

	public THttpClient(){

	}
	public THttpClient(String uri) throws IOException{
		this.url = new URL(uri);
		this.connection = (HttpURLConnection) url.openConnection();
		this.connection.setDoOutput(true);
	}

	public void addRequestProperty(String key, String value) {
		this.connection.addRequestProperty(key, value);
	}


	public Object get() throws Exception {
		try {
			if(connection==null){
				throw new Exception("THttpClient is null. Connection cannot be null.");
			}
			String url = this.url.toString();
			if(url!=null && url.contains("outputMode=json")){
				return getJsonResponse();
			}else{
				return getResponse();
			}
		} catch (Exception e) {
			e.getCause();
			throw new Exception(e.getMessage());
		}finally{
			this.destroy();
		}
	}

	public Object post(JSONObject params) throws Exception {
		try {
			if(connection==null){
				throw new Exception("THttpClient is null. Connection cannot be null.");
			}
			this.connection.addRequestProperty("Content-Length",Integer.toString(params.toString().length()));
			this.connection.addRequestProperty("Content-Type", "application/json");

			DataOutputStream ostream = new DataOutputStream(this.connection.getOutputStream());

			ostream.write(params.toString().getBytes());
			ostream.close();

			if(params.has("outputMode") && params.getString("outputMode").equals("json"))
				return getJsonResponse();
			else
				return getResponse();
		} catch (Exception e) {
			throw new Exception(e);
		} finally{
			this.destroy();
		}

	}

	public JSONObject post(Map<String, Object> params)
			throws Exception {
		try {
			if(connection==null){
				throw new Exception("THttpClient is null. Connection cannot be null.");
			}
			StringBuilder data = new StringBuilder();
			Iterator<String> iterator = params.keySet().iterator();
			while (iterator.hasNext()) {
				String key = (String) iterator.next();
				data.append("&" + key + "=" + params.get(key));
			}
			data.replace(0, 1, "").trimToSize();

			this.connection.addRequestProperty("Content-Length",
					Integer.toString(data.length()));
			this.connection.addRequestProperty("Content-Type", "application/json");

			DataOutputStream ostream = new DataOutputStream(
					this.connection.getOutputStream());
			ostream.write(data.toString().getBytes());
			ostream.close();
			return getJsonResponse();
		} catch (Exception e) {
			throw new Exception(e);
		} finally{
			this.destroy();
		}

	}

	public String getResponse() throws Exception, IOException{

		DataInputStream istream = new DataInputStream(
				this.connection.getInputStream());

		String status_msg =  this.connection.getResponseMessage();
		int status_code = this.connection.getResponseCode();

		if(status_code!=200){
			throw new RuntimeException("Failed : HTTP error code : " + status_code);
		}

		String response = null;
		try {
			BufferedReader streamReader = new BufferedReader(
					new InputStreamReader(istream, "UTF-8"));

			StringBuilder responseStrBuilder = new StringBuilder();

			String inputStr;

			while ((inputStr = streamReader.readLine()) != null) {
				responseStrBuilder.append(inputStr);
			}
			responseStrBuilder.append("status_msg="+status_msg+";");
			responseStrBuilder.append("status_code="+status_code+";");
			response = responseStrBuilder.toString();
		}catch (Exception e) {
			throw new Exception(e.getMessage());
		} finally {
			istream.close();
		}
		return response;
	}

	private JSONObject getJsonResponse() throws Exception,
	IOException {

		String status_msg =  this.connection.getResponseMessage();
		int status_code = this.connection.getResponseCode();

		if(status_code!=200){
			throw new RuntimeException("Failed : HTTP error code : " + status_code);
		}

		DataInputStream istream = new DataInputStream(
				this.connection.getInputStream());

		JSONObject response = null;
		try {
			BufferedReader streamReader = new BufferedReader(
					new InputStreamReader(istream, "UTF-8"));

			StringBuilder responseStrBuilder = new StringBuilder();

			String inputStr;

			while ((inputStr = streamReader.readLine()) != null) {
				responseStrBuilder.append(inputStr);
			}
			response = new JSONObject(responseStrBuilder.toString());

			response.put("status_msg", status_msg);
			response.put("status_code", status_code);

		} catch (Exception e) {
			throw new Exception(e.getMessage());
		} finally {
			istream.close();
		}
		return response;
	}

	public Object put(Map<String, Object> params) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	public void destroy() throws Exception {
		if(connection!=null){
			this.connection.disconnect();
			this.connection = null ;
		}
	}

	public void connect(Map<String, Object> params) throws Exception {
		String uri = (String)params.get("url");
		try {
			this.url = new URL(uri);
			this.connection = (HttpURLConnection) url.openConnection();
			this.connection.setDoOutput(true);
		} catch (IOException e) {
			throw new Exception(e);
		}

	}
}
