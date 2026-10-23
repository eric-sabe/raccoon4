package com.akdeniz.googleplaycrawler;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;

import com.akdeniz.googleplaycrawler.misc.Base64;

/**
 * ClientLogin implementation.
 * 
 * @author patrick
 * 
 */
class Identity {

	private static final String LOGIN_URL = "https://android.clients.google.com/auth";
	private static final String PUBKEY = "AAAAgMom/1a/v0lblO2Ubrt60J2gcuXSljGFQXgcyZWveWLEwo6prwgi3iJIZdodyhKZQrNWp5nKJ3srRXcUW+F1BD3baEVGcmEgqaLZUNBjm057pKRI16kB0YppeGx5qIQ5QjKzsR8ETQbKLNWgRY0QRNVz34kMJR3P/LgHax/6rmf5AAAAAwEAAQ==";
	private static final String SERVICE = "oauth2:https://www.googleapis.com/auth/googleplay";
	private static final String FALLBACK_SERVICE = "androidmarket";
	private static final String CLIENT_SIG = "38918a453d07199354f8b19af05ec6562ced5788";

	private String firstName;
	private String lastName;
	private String email;
	private String services;
	private String authToken;
	private String masterToken;

	private Identity() {
	}

	/**
	 * Long-lived AAS master token (aas_et/...) if returned or used during sign-in.
	 */
	public String getMasterToken() {
		return masterToken;
	}

	/**
	 * Normalize a password or token string (e.g. strip spaces from 16-char Google
	 * App Passwords).
	 */
	public static String normalizeCredential(String pwd) {
		if (pwd == null) {
			return "";
		}
		String trimmed = pwd.trim();
		if (trimmed.matches("[a-zA-Z0-9]{4}( +[a-zA-Z0-9]{4}){3}")) {
			return trimmed.replaceAll("\\s+", "");
		}
		return trimmed;
	}

	/**
	 * User's first name
	 * 
	 * @return the first name, retrieved from Play
	 */
	public String getFirstName() {
		return firstName;
	}

	/**
	 * User's last name
	 * 
	 * @return the lastname, retrieved from Play
	 */
	public String getLastName() {
		return lastName;
	}

	/**
	 * User's current email address
	 * 
	 * @return the email address, retrieved from Play.
	 */
	public String getEmail() {
		return email;
	}

	/**
	 * List the services, the user is clear for.
	 * 
	 * @return list of service names. Potentially empty, never null
	 */
	public List<String> listServices() {
		ArrayList<String> ret = new ArrayList<String>();
		if (services != null) {
			String[] tmp = services.split(" *, *");
			for (String s : tmp) {
				ret.add(s);
			}
		}
		return ret;
	}

	/**
	 * Get the session cookie
	 * 
	 * @return a token that can be used for getting access.
	 */
	public String getAuthToken() {
		return authToken;
	}

	/**
	 * Sing into Play
	 * 
	 * @param req
	 *          parameter object with username, password and locale.
	 * @return new instance
	 * @throws BadAuthenticationException
	 * @throws ClientProtocolException
	 * @throws HttpResponseException
	 * @throws IOException
	 */
	public static Identity signIn(HttpClient client, String uid, String pwd)
			throws ClientProtocolException,
			HttpResponseException, IOException {

		Locale loc = Locale.getDefault();
		String cred = normalizeCredential(pwd);

		if (cred.startsWith("ya29.") || cred.startsWith("weblogin:")) {
			Identity direct = new Identity();
			direct.email = uid;
			direct.authToken = cred;
			return direct;
		}

		String discoveredMasterToken = null;
		if (cred.startsWith("oauth2_4/")) {
			List<NameValuePair> exchangeParams = new ArrayList<NameValuePair>();
			exchangeParams.add(new BasicNameValuePair("Email", uid));
			exchangeParams.add(new BasicNameValuePair("Token", cred));
			exchangeParams.add(new BasicNameValuePair("ACCESS_TOKEN", "1"));
			exchangeParams.add(new BasicNameValuePair("accountType", "HOSTED_OR_GOOGLE"));
			exchangeParams.add(new BasicNameValuePair("has_permission", "1"));
			exchangeParams.add(new BasicNameValuePair("add_account", "1"));
			exchangeParams.add(new BasicNameValuePair("service", "ac2dm"));
			exchangeParams.add(new BasicNameValuePair("source", "android"));
			exchangeParams.add(new BasicNameValuePair("sdk_version", "33"));
			exchangeParams.add(new BasicNameValuePair("client_sig", CLIENT_SIG));
			exchangeParams.add(new BasicNameValuePair("callerSig", CLIENT_SIG));
			if (loc != null) {
				exchangeParams.add(new BasicNameValuePair("device_country", loc.getLanguage()));
				exchangeParams.add(new BasicNameValuePair("lang", loc.getLanguage()));
			}
			Map<String, String> exMap = doPost(client, exchangeParams);
			if (exMap.containsKey("Token")) {
				cred = exMap.get("Token");
				discoveredMasterToken = cred;
			}
		}

		String epwd = null;
		if (cred.startsWith("aas_et/")) {
			epwd = cred;
			discoveredMasterToken = cred;
		}
		else {
			try {
				epwd = encryptString(uid + "\u0000" + cred);
			}
			catch (Exception e) {
				throw new RuntimeException("Could not encrypt password", e);
			}
		}

		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("Email", uid));
		params.add(new BasicNameValuePair("EncryptedPasswd", epwd));
		params.add(new BasicNameValuePair("service", SERVICE));
		params.add(new BasicNameValuePair("add_account", "1"));
		params.add(new BasicNameValuePair("sdk_version", "33"));
		params.add(new BasicNameValuePair("accountType", "HOSTED_OR_GOOGLE"));
		params.add(new BasicNameValuePair("has_permission", "1"));
		params.add(new BasicNameValuePair("source", "android"));
		params.add(new BasicNameValuePair("app", "com.android.vending"));
		params.add(new BasicNameValuePair("client_sig", CLIENT_SIG));
		params.add(new BasicNameValuePair("callerPkg", "com.android.vending"));
		params.add(new BasicNameValuePair("callerSig", CLIENT_SIG));
		if (loc != null) {
			params.add(new BasicNameValuePair("device_country", loc.getLanguage()));
			params.add(new BasicNameValuePair("lang", loc.getLanguage()));
		}

		Map<String, String> map = doPost(client, params);
		Identity ret = new Identity();
		ret.firstName = map.get("firstName");
		ret.lastName = map.get("lastName");
		ret.email = map.get("Email");
		ret.services = map.get("services");
		ret.authToken = map.get("Auth");
		String tok = map.get("Token");
		if (tok != null && tok.length() > 0) {
			discoveredMasterToken = tok;
		}
		ret.masterToken = discoveredMasterToken;

		if (tok != null && tok.length() > 0 && !cred.startsWith("aas_et/")
				&& (ret.authToken == null || !ret.authToken.startsWith("ya29."))) {
			// Since mid Oct/2017, "Token" must be sent back if account details
			// (first-, lastname, services) are requested by "add_account".
			// Otherwise the "Auth" cookie will have a short TTL.
			params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair("Authorization", "GoogleLogin auth="
					+ map.get("Auth")));
			params.add(new BasicNameValuePair("Token", tok));
			params.add(new BasicNameValuePair("token_request_options", "CAA4AQ=="));
			params.add(new BasicNameValuePair("service", FALLBACK_SERVICE));
			params.add(new BasicNameValuePair("accountType", "HOSTED_OR_GOOGLE"));
			params.add(new BasicNameValuePair("app", "com.android.vending"));
			params.add(new BasicNameValuePair("client_sig", CLIENT_SIG));
			map = doPost(client, params);
			ret.authToken = map.get("Auth");
		}
		return ret;
	}

	private static Map<String, String> doPost(HttpClient client,
			List<NameValuePair> params) throws ClientProtocolException,
			 IOException {
		HttpPost httppost = new HttpPost(LOGIN_URL);
		httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
		HttpResponse response = client.execute(httppost);
		Map<String, String> map = parseContent(response.getEntity().getContent());
		if (response.getStatusLine().getStatusCode() == 200) {
			return map;
		}

		if (map.containsKey("Error")) {
			throw new ClientProtocolException(map.get("Error"));
		}
		throw new HttpResponseException(response.getStatusLine().getStatusCode(),
				response.getStatusLine().getReasonPhrase());
	}

	private static Map<String, String> parseContent(InputStream in)
			throws IOException {
		HashMap<String, String> ret = new HashMap<String, String>();
		int k = 0;
		boolean value = false; // Simple state machine.
		StringBuilder key = new StringBuilder();
		StringBuilder val = new StringBuilder();
		while (true) {
			k = in.read();
			if (k == -1) { // EOF
				ret.put(key.toString(), val.toString());
				break;
			}
			if (k == '=') { // Skip symbol; toggle state
				value = true;
				continue;
			}
			if (k == '\n') { // End of value -> commit key/value pair
				value = false;
				ret.put(key.toString(), val.toString());
				key.setLength(0);
				val.setLength(0);
				continue;
			}
			if (k == '\r') { // Skip line end symbol.
				continue;
			}
			if (value) { // Depending on state either file into key or value
				val.append((char) k);
			}
			else {
				key.append((char) k);
			}
		}
		return ret;
	}

	private static String encryptString(String str)
			throws NoSuchAlgorithmException, InvalidKeySpecException,
			NoSuchPaddingException, UnsupportedEncodingException,
			InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

		int i = 0;

		byte[] obj = new byte[5];
		Key createKeyFromString = createKeyFromString(PUBKEY, obj);
		if (createKeyFromString == null) {
			return null;
		}

		Cipher instance = Cipher.getInstance("RSA/ECB/OAEPWITHSHA1ANDMGF1PADDING");
		byte[] bytes = str.getBytes("UTF-8");
		int length = ((bytes.length - 1) / 86) + 1;
		byte[] obj2 = new byte[(length * 133)];
		while (i < length) {
			instance.init(1, createKeyFromString);
			byte[] doFinal = instance.doFinal(bytes, i * 86,
					i == length + -1 ? bytes.length - (i * 86) : 86);
			System.arraycopy(obj, 0, obj2, i * 133, obj.length);
			System
					.arraycopy(doFinal, 0, obj2, (i * 133) + obj.length, doFinal.length);
			i++;
		}
		return Base64.encodeToString(obj2, 10);
	}

	private static PublicKey createKeyFromString(String str, byte[] bArr)
			throws NoSuchAlgorithmException, InvalidKeySpecException {
		byte[] decode = Base64.decode(str, 0);
		int readInt = readInt(decode, 0);
		byte[] obj = new byte[readInt];
		System.arraycopy(decode, 4, obj, 0, readInt);
		BigInteger bigInteger = new BigInteger(1, obj);
		int readInt2 = readInt(decode, readInt + 4);
		byte[] obj2 = new byte[readInt2];
		System.arraycopy(decode, readInt + 8, obj2, 0, readInt2);
		BigInteger bigInteger2 = new BigInteger(1, obj2);
		decode = MessageDigest.getInstance("SHA-1").digest(decode);
		bArr[0] = (byte) 0;
		System.arraycopy(decode, 0, bArr, 1, 4);
		return KeyFactory.getInstance("RSA").generatePublic(
				new RSAPublicKeySpec(bigInteger, bigInteger2));

	}

	private static int readInt(byte[] bArr, int i) {
		return (((((bArr[i] & 255) << 24) | 0) | ((bArr[i + 1] & 255) << 16)) | ((bArr[i + 2] & 255) << 8))
				| (bArr[i + 3] & 255);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(firstName);
		sb.append(' ');
		sb.append(lastName);
		sb.append('<');
		sb.append(email);
		sb.append('>');
		sb.append('\t');
		sb.append(services);
		sb.append('\t');
		sb.append(authToken);
		return sb.toString();
	}

}
