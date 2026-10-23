/*
 * Copyright 2015 Patrick Ahlbrecht
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.onyxbits.raccoon.ptools;

import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An Android device
 * 
 * @author patrick
 * 
 */
public class Device {

	/**
	 * The serial number of the device
	 */
	public final String serial;

	private final String state;
	private String host;
	private int port;

	private ArrayList<String> libs;
	private ArrayList<String> features;
	
	private Properties propertyCache;

	protected Device(String serial) {
		this("localhost", 5037, serial, "device");
	}

	public Device(String serial, String state) {
		this("localhost", 5037, serial, state);
	}

	protected Device(String host, int port, String serial) {
		this(host, port, serial, "device");
	}

	protected Device(String host, int port, String serial, String state) {
		this.host = host != null ? host : "localhost";
		this.port = port;
		this.serial = serial;
		this.state = state != null ? state.trim() : "device";
		this.propertyCache = new Properties();
	}

	public String getSerial() {
		return serial;
	}

	public String getState() {
		return state;
	}

	public boolean isAuthorized() {
		return "device".equals(state);
	}

	static void readFully(InputStream in, byte[] buf, int offset, int len)
			throws IOException {
		int total = 0;
		while (total < len) {
			int r = in.read(buf, offset + total, len - total);
			if (r < 0) {
				throw new EOFException("Unexpected EOF from ADB daemon");
			}
			total += r;
		}
	}

	/**
	 * Read a system property
	 * 
	 * @param name
	 *          property name
	 * @param def
	 *          default value to return if name not found.
	 * @return the value or the default value.
	 */
	public String getProperty(String name, String def) {
		if (propertyCache.containsKey(name)) {
			return propertyCache.getProperty(name);
		}
		if ("ro.serialno".equals(name)) {
			// On Android 9+, ro.serialno returns "unknown" for non-root shell.
			return serial;
		}
		String tmp = null;
		try {
			// Note to self: the Android property system sources more than just
			// /system/build.prop, so there is no way to prefetch everything.
			tmp = exec("getprop " + name).trim();
		}
		catch (IOException e) {
		}
		if (tmp == null || tmp.length() == 0 || "unknown".equalsIgnoreCase(tmp)) {
			return def;
		}
		propertyCache.put(name, tmp);
		return tmp;
	}

	/**
	 * Create a stream for reading a file from the device
	 * 
	 * @param fname
	 *          remote filename
	 * @throws IOException
	 */
	public InputStream createPullStream(String fname) throws IOException {
		Socket socket = null;
		try {
			socket = new Socket(host, port);
			InputStream in = socket.getInputStream();
			OutputStream out = socket.getOutputStream();
			DataOutputStream output = new DataOutputStream(socket.getOutputStream());
			ProtocolSupport.send("host:transport:" + serial, in, out);

			ProtocolSupport.send("sync:", in, out);
			output.writeBytes("RECV");
			output.writeInt(Integer.reverseBytes(fname.length()));
			output.writeBytes(fname);
			output.flush();
			return new PullStream(in);
		}
		catch (IOException e) {
			try {
				if (socket != null) {
					socket.close();
				}
			}
			catch (IOException e1) {
			}
			throw e;
		}
	}

	/**
	 * Create a stream for copying a file to the device
	 * 
	 * @param fname
	 *          remote filename
	 * @param mode
	 *          access flags (e.g. 0644)
	 * @throws IOException
	 */
	public PushStream createPushStream(String fname, int mode) throws IOException {
		Socket socket = null;
		try {
			socket = new Socket(host, port);
			InputStream in = socket.getInputStream();
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());
			ProtocolSupport.send("host:transport:" + serial, in, out);

			ProtocolSupport.send("sync:", in, out);
			out.writeBytes("SEND");
			String target = fname + "," + mode;
			out.writeInt(Integer.reverseBytes(target.length()));
			out.writeBytes(target);
			out.flush();
			return new PushStream(out);
		}
		catch (IOException e) {
			try {
				if (socket != null) {
					socket.close();
				}
			}
			catch (IOException e1) {
			}
			throw e;
		}
	}

	/**
	 * Execute a shell command on the device
	 * 
	 * @param cmd
	 *          the command
	 * @return whatever the command prints to sdtout/stderr
	 * @throws IOException
	 */
	public String exec(String cmd) throws IOException {
		Socket socket = null;
		try {
			socket = new Socket(host, port);
			InputStream in = socket.getInputStream();
			OutputStream out = socket.getOutputStream();
			ProtocolSupport.send("host:transport:" + serial, in, out);

			ProtocolSupport.send("shell:" + cmd, in, out);
			int i = -1;
			StringBuilder sb = new StringBuilder();
			while ((i = in.read()) != -1) {
				sb.append((char) i);
			}
			return sb.toString();
		}
		finally {
			if (socket != null) {
				try {
					socket.close();
				}
				catch (IOException e1) {
				}
			}
		}
	}

	/**
	 * Get file info
	 * 
	 * @param remotePath
	 *          the file to stat
	 * @return file state
	 * @throws IOException
	 */
	public RemoteFile stat(String remotePath) throws IOException {
		Socket socket = null;
		try {
			socket = new Socket(host, port);
			InputStream in = socket.getInputStream();
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());
			ProtocolSupport.send("host:transport:" + serial, in, out);

			ProtocolSupport.send("sync:", in, out);
			out.writeBytes("STAT");
			out.writeInt(Integer.reverseBytes(remotePath.length()));
			out.writeBytes(remotePath);

			byte[] buf = new byte[4];
			readFully(in, buf, 0, 4);
			if (ProtocolSupport.parseInt(buf, 0) == ProtocolSupport.STAT) {
				buf = new byte[12];
				readFully(in, buf, 0, 12);
				return new RemoteFile(remotePath, ProtocolSupport.parseInt(buf, 0),
						ProtocolSupport.parseInt(buf, 4), ProtocolSupport.parseInt(buf, 8));
			}
			throw new RuntimeException("File did not stat!");
		}
		finally {
			if (socket != null) {
				try {
					socket.close();
				}
				catch (IOException e1) {
				}
			}
		}
	}

	/**
	 * List a directory
	 * 
	 * @param remotePath
	 *          directory name
	 * @return file listing.
	 * @throws IOException
	 */
	public List<RemoteFile> list(String remotePath) throws IOException {
		Socket socket = null;
		try {
			socket = new Socket(host, port);
			InputStream in = socket.getInputStream();
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());
			ProtocolSupport.send("host:transport:" + serial, in, out);

			ProtocolSupport.send("sync:", in, out);
			out.writeBytes("LIST");
			out.writeInt(Integer.reverseBytes(remotePath.length()));
			out.writeBytes(remotePath);
			byte[] buf = new byte[512];
			List<RemoteFile> result = new Vector<RemoteFile>();
			readFully(in, buf, 0, 4);
			while (ProtocolSupport.parseInt(buf, 0) == ProtocolSupport.DENT) {
				readFully(in, buf, 0, 16);
				int len = ProtocolSupport.parseInt(buf, 12);
				if (len > buf.length) {
					buf = new byte[len];
				}
				readFully(in, buf, 0, len);
				String fname = new String(buf, 0, len);
				result
						.add(new RemoteFile(fname, ProtocolSupport.parseInt(buf, 0),
								ProtocolSupport.parseInt(buf, 4), ProtocolSupport.parseInt(buf,
										8)));
				readFully(in, buf, 0, 4);
			}
			return result;
		}
		finally {
			if (socket != null) {
				try {
					socket.close();
				}
				catch (IOException e1) {
				}
			}
		}
	}

	/**
	 * Get the external storage path
	 * 
	 * @return the external storage directory (guaranteed to include a trailing
	 *         slash).
	 */
	public String getExternalStorageDir() throws IOException {
		String ret = exec("echo $EXTERNAL_STORAGE").trim();
		if (ret.isEmpty()) {
			ret = "/sdcard/";
		}
		if (!(ret.endsWith("/"))) {
			ret += "/";
		}
		return ret;
	}

	/**
	 * Get the tmp directory of the user partition
	 * 
	 * @return a pathname (trailing slash included).
	 * @throws IOException
	 */
	public String getUserTempDir() throws IOException {
		return "/data/local/tmp/";
	}

	/**
	 * Attempt to query the device's Google Services Framework (GSF) ID in hex.
	 * 
	 * @return hex GSF ID or null if unavailable.
	 */
	public String getGsfId() {
		try {
			String out = exec(
					"content query --uri content://com.google.android.gsf.gservices/prefix --projection android_id");
			Matcher m = Pattern.compile("android_id=([0-9]+)").matcher(out);
			if (m.find()) {
				return new BigInteger(m.group(1)).toString(16);
			}
		}
		catch (Exception e) {
		}
		try {
			String out = exec(
					"su -c 'cat /data/data/com.google.android.gms/shared_prefs/Checkin.xml'");
			Matcher m = Pattern.compile(
					"<string\\s+name=\"android_id\">([0-9]+)</string>").matcher(out);
			if (m.find()) {
				return new BigInteger(m.group(1)).toString(16);
			}
		}
		catch (Exception e) {
		}
		try {
			String out = exec(
					"su -c \"sqlite3 /data/data/com.google.android.gsf/databases/gservices.db 'select value from main where name = \\\"android_id\\\";'\"")
					.trim();
			if (out.matches("[0-9]+")) {
				return new BigInteger(out).toString(16);
			}
		}
		catch (Exception e) {
		}
		return null;
	}

	/**
	 * Query the primary Google account email signed in on the device (requires
	 * root or dumpsys access).
	 * 
	 * @return Google account email or null if unavailable.
	 */
	public String getGoogleAccountEmail() {
		try {
			String out = exec(
					"su -c 'cat /data/data/com.google.android.gms/shared_prefs/Checkin.xml'");
			Matcher m = Pattern.compile("authAccount(?:&quot;|\"):(?:&quot;|\")([^\"&]+@[^\"&]+)(?:&quot;|\")")
					.matcher(out);
			if (m.find()) {
				return m.group(1).trim();
			}
		}
		catch (Exception e) {
		}
		try {
			String out = exec("dumpsys account");
			Matcher m = Pattern.compile(
					"Account\\s*\\{\\s*name=([^,\\s}]+@[^,\\s}]+),\\s*type=com\\.google\\s*\\}")
					.matcher(out);
			if (m.find()) {
				return m.group(1).trim();
			}
		}
		catch (Exception e) {
		}
		return null;
	}

	/**
	 * Query the Google Play Store OAuth2 token (ya29...) cached on a rooted
	 * Android device.
	 * 
	 * @param refresh
	 *          true to wake/launch Play Store first so GMS refreshes an expired
	 *          token
	 * @return ya29... OAuth2 token or null if unavailable
	 */
	public String getGooglePlayToken(boolean refresh) {
		try {
			if (refresh) {
				exec("am start -n com.android.vending/.AssetBrowserActivity >/dev/null 2>&1; sleep 2");
			}
			String out = exec(
					"su -c 'strings /data/system_ce/0/accounts_ce.db-wal /data/system_ce/0/accounts_ce.db 2>/dev/null'");
			Matcher m = Pattern
					.compile(
							"com\\.android\\.vending:[0-9a-f]+:oauth2:https://www\\.googleapis\\.com/auth/googleplay(ya29\\.[A-Za-z0-9._-]+)")
					.matcher(out);
			if (m.find()) {
				return m.group(1).trim();
			}
		}
		catch (Exception e) {
		}
		return null;
	}

	/**
	 * Get the installed Google Play Store (com.android.vending) versionName.
	 * 
	 * @return versionName or null if unavailable
	 */
	public String getPlayStoreVersionName() {
		try {
			String out = exec("dumpsys package com.android.vending");
			Matcher m = Pattern.compile("versionName=([^\\s\\r\\n]+)").matcher(out);
			if (m.find()) {
				return m.group(1).trim();
			}
		}
		catch (Exception e) {
		}
		return null;
	}

	/**
	 * Get the installed Google Play Store (com.android.vending) versionCode.
	 * 
	 * @return versionCode or -1 if unavailable
	 */
	public int getPlayStoreVersionCode() {
		try {
			String out = exec("dumpsys package com.android.vending");
			Matcher m = Pattern.compile("versionCode=([0-9]+)").matcher(out);
			if (m.find()) {
				return Integer.parseInt(m.group(1).trim());
			}
		}
		catch (Exception e) {
		}
		return -1;
	}

	/**
	 * List installed package names on the device.
	 * 
	 * @param thirdPartyOnly
	 *          true to list only user-installed (-3) apps, false for all packages
	 * @return sorted list of package names
	 */
	public List<String> getInstalledPackages(boolean thirdPartyOnly)
			throws IOException {
		String cmd = thirdPartyOnly ? "pm list packages -3" : "pm list packages";
		String[] lines = exec(cmd).split("\\r?\\n");
		List<String> result = new ArrayList<String>();
		for (String line : lines) {
			line = line.trim();
			if (line.startsWith("package:")) {
				String pkg = line.substring("package:".length()).trim();
				if (!pkg.isEmpty()) {
					result.add(pkg);
				}
			}
		}
		Collections.sort(result);
		return result;
	}

	/**
	 * Get the remote APK file paths on the device for a given package name.
	 * 
	 * @param packageName
	 *          Android package name
	 * @return list of remote APK paths (base.apk and any split APKs)
	 */
	public List<String> getApkPaths(String packageName) throws IOException {
		String[] lines = exec("pm path " + packageName).split("\\r?\\n");
		List<String> paths = new ArrayList<String>();
		for (String line : lines) {
			line = line.trim();
			if (line.startsWith("package:")) {
				String path = line.substring("package:".length()).trim();
				if (!path.isEmpty()) {
					paths.add(path);
				}
			}
		}
		return paths;
	}

	/**
	 * List available libraries
	 * 
	 * @return installed libraries
	 */
	public List<String> getSharedLibraries() {
		if (libs == null) {
			libs = new ArrayList<String>();
			try {
				String[] output = exec("pm list libraries").split("\\r?\\n");
				for (String line : output) {
					String[] tmp = line.split(":");
					if (tmp.length == 2) {
						libs.add(tmp[1].trim());
					}
				}
			}
			catch (Exception e) {
			}
		}
		return libs;
	}

	/**
	 * List available features
	 * 
	 * @return device features
	 */
	public List<String> getSystemFeatures() {
		if (features == null) {
			features = new ArrayList<String>();
			try {
				String[] output = exec("pm list features").split("\\r?\\n");
				for (String line : output) {
					String[] tmp = line.split(":");
					if (tmp.length == 2) {
						String feat = tmp[1].trim();
						int eq = feat.indexOf('=');
						if (eq > 0) {
							feat = feat.substring(0, eq).trim();
						}
						if (!feat.isEmpty() && !"reqGlEsVersion".equals(feat)) {
							features.add(feat);
						}
					}
				}
			}
			catch (Exception e) {
			}
		}
		return features;
	}

	/**
	 * Query physical screen width and height in pixels via "wm size".
	 * 
	 * @return two-element array [width, height] or [1080, 2400] on fallback
	 */
	public int[] getScreenSize() {
		try {
			String out = exec("wm size");
			Matcher m = Pattern.compile("([0-9]+)x([0-9]+)").matcher(out);
			if (m.find()) {
				return new int[] {
						Integer.parseInt(m.group(1)),
						Integer.parseInt(m.group(2)) };
			}
		}
		catch (Exception e) {
		}
		return new int[] { 1080, 2400 };
	}
}