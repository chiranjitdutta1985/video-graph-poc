package com.timeinc.assetmgr.video;

import java.util.Map;

import javax.naming.CommunicationException;

import org.json.JSONObject;

/**
 * 
 * @author Ritu-Bhandari
 *
 */

public interface IHttpClient {

	Object get() throws Exception;

	Object post(Map<String, Object> params) throws Exception;

	/**
	 * For Content-Type : application/json
	 * @param params
	 * @return
	 * @throws CommunicationException
	 */
	Object post(JSONObject params) throws Exception;

	Object put(Map<String, Object> params) throws Exception;

	void addRequestProperty(String key, String value);

	void connect(Map<String, Object> params) throws Exception ;

	void destroy() throws Exception;

}
