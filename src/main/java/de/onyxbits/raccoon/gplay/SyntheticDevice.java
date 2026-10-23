/*
 * Copyright 2017 Patrick Ahlbrecht
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
package de.onyxbits.raccoon.gplay;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * A completely synthetic device that is configured through a DummyDroid /
 * build.prop properties file.
 * 
 * @author patrick
 * 
 */
public class SyntheticDevice extends MockDevice {

	public static final String SCREENWIDTH = "dd.screenwidth";
	public static final String SCREENHEIGHT = "dd.screenheight";
	public static final String LOCALE = "dd.locale";
	public static final String TIMEZONE = "dd.timezone";
	public static final String CELLOPERATOR = "dd.celloperator";
	public static final String SIMOPERATOR = "dd.simoperator";
	public static final String FEATURES = "dd.features";
	public static final String LIBRARIES = "dd.libraries";
	public static final String PLATFORMS = "dd.native";
	public static final String LOCALES = "dd.locales";
	public static final String FINSKY_VERSION = "dd.finsky.version";
	public static final String FINSKY_VERSIONCODE = "dd.finsky.versioncode";

	private Properties device;

	public SyntheticDevice(Properties device) {
		Properties merged = loadDefaultProperties();
		if (device != null) {
			for (String name : device.stringPropertyNames()) {
				merged.setProperty(name, device.getProperty(name));
			}
		}
		this.device = merged;
	}

	/**
	 * Load the built-in DummyDroid preset (Google Pixel 7a, Android 15).
	 */
	public static SyntheticDevice createDefault() {
		return new SyntheticDevice(loadDefaultProperties());
	}

	/**
	 * Load a SyntheticDevice from a DummyDroid .properties or Android build.prop
	 * file, falling back to the built-in Pixel 7a defaults for any omitted keys.
	 */
	public static SyntheticDevice fromFile(File file) throws IOException {
		Properties props = new Properties();
		FileInputStream fis = new FileInputStream(file);
		try {
			props.load(fis);
		}
		finally {
			fis.close();
		}
		return new SyntheticDevice(props);
	}

	public static Properties loadDefaultProperties() {
		Properties props = new Properties();
		InputStream in = SyntheticDevice.class
				.getResourceAsStream("pixel7a.properties");
		if (in != null) {
			try {
				props.load(in);
			}
			catch (IOException e) {
			}
			finally {
				try {
					in.close();
				}
				catch (IOException e) {
				}
			}
		}
		return props;
	}

	public Properties getProperties() {
		return device;
	}

	/**
	 * Convert any {@link MockDevice} into DummyDroid {@link Properties}.
	 */
	public static Properties toProperties(MockDevice dev) {
		Properties p = new Properties();
		p.setProperty("ro.build.version.sdk", String.valueOf(dev.getSdkVersion()));
		p.setProperty("ro.build.version.release",
				nonNull(dev.getReleaseVersion(), "15"));
		p.setProperty("ro.opengles.version", String.valueOf(dev.getGlEsVersion()));
		p.setProperty("ro.sf.lcd_density", String.valueOf(dev.getScreenDensity()));
		p.setProperty(SCREENWIDTH, String.valueOf(
				dev.getScreenWidth() > 0 ? dev.getScreenWidth() : 1080));
		p.setProperty(SCREENHEIGHT, String.valueOf(
				dev.getScreenHeight() > 0 ? dev.getScreenHeight() : 2400));
		p.setProperty("ro.build.fingerprint", nonNull(dev.getFingerprint(), ""));
		p.setProperty("ro.product.board",
				nonNull(dev.getProductBoard(), dev.getDevice()));
		p.setProperty("ro.carrier", nonNull(dev.getCarrier(), "unknown"));
		p.setProperty("ro.bootloader", nonNull(dev.getBootloader(), ""));
		p.setProperty("ro.com.google.clientidbase",
				nonNull(dev.getClientIdBase(), "android-google"));
		p.setProperty("ro.product.device", nonNull(dev.getDevice(), "lynx"));
		p.setProperty("ro.hardware", nonNull(dev.getHardware(), dev.getDevice()));
		p.setProperty("ro.product.model", nonNull(dev.getModel(), "Pixel 7a"));
		p.setProperty("ro.product.manufacturer",
				nonNull(dev.getManufacturer(), "Google"));
		p.setProperty("ro.product.name",
				nonNull(dev.getBuildProduct(), dev.getDevice()));
		p.setProperty("ro.build.product",
				nonNull(dev.getBuildProduct(), dev.getDevice()));
		p.setProperty("ro.build.id", nonNull(dev.getBuildId(), ""));
		p.setProperty("ro.build.type", nonNull(dev.getBuildType(), "user"));
		p.setProperty("gsm.version.baseband", nonNull(dev.getRadio(), ""));
		p.setProperty(FINSKY_VERSION, nonNull(dev.getFinskyVersion(), "53.6.15-34"));
		p.setProperty(FINSKY_VERSIONCODE,
				String.valueOf(dev.getFinskyVersionCode()));
		p.setProperty(LOCALE, nonNull(dev.getLocale(), "en_US"));
		p.setProperty(TIMEZONE, nonNull(dev.getTimeZone(), "America/New_York"));
		p.setProperty(CELLOPERATOR, nonNull(dev.getCellOperator(), "310260"));
		p.setProperty(SIMOPERATOR, nonNull(dev.getSimOperator(), "310260"));
		p.setProperty(PLATFORMS, joinList(dev.getNativePlatforms()));
		p.setProperty("ro.product.cpu.abilist", joinList(dev.getNativePlatforms()));
		p.setProperty(LIBRARIES, joinList(dev.getSharedLibraries()));
		p.setProperty(FEATURES, joinList(dev.getSystemFeatures()));
		return p;
	}

	public static void convert(RealDevice device, PrintWriter writer)
			throws IOException {
		convert((MockDevice) device, writer);
	}

	public static void convert(MockDevice device, PrintWriter writer)
			throws IOException {
		Properties p = toProperties(device);
		List<String> keys = new ArrayList<String>(p.stringPropertyNames());
		Collections.sort(keys);
		writer.println("# DummyDroid profile exported by Raccoon");
		for (String k : keys) {
			writer.println(k + "=" + p.getProperty(k));
		}
		writer.flush();
	}

	private static String nonNull(String val, String def) {
		return (val != null && !val.isEmpty()) ? val : def;
	}

	private static String joinList(List<String> list) {
		if (list == null || list.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < list.size(); i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(list.get(i));
		}
		return sb.toString();
	}

	private static List<String> splitList(String csv) {
		List<String> ret = new ArrayList<String>();
		if (csv == null || csv.trim().isEmpty()) {
			return ret;
		}
		for (String item : csv.trim().split(" *, *")) {
			if (!item.isEmpty()) {
				ret.add(item);
			}
		}
		return ret;
	}

	private int getInt(String key, int def) {
		try {
			String v = device.getProperty(key);
			if (v != null && !v.trim().isEmpty()) {
				return Integer.parseInt(v.trim());
			}
		}
		catch (NumberFormatException e) {
		}
		return def;
	}

	@Override
	public int getFinskyVersionCode() {
		return getInt(FINSKY_VERSIONCODE, 85361540);
	}

	@Override
	public String getFinskyVersion() {
		return device.getProperty(FINSKY_VERSION, "53.6.15-34");
	}

	@Override
	public int getSdkVersion() {
		return getInt("ro.build.version.sdk", 35);
	}

	@Override
	public String getReleaseVersion() {
		return device.getProperty("ro.build.version.release", "15");
	}

	@Override
	public String getDevice() {
		return device.getProperty("ro.product.device", "lynx");
	}

	@Override
	public String getHardware() {
		return device.getProperty("ro.hardware", getDevice());
	}

	@Override
	public String getBuildProduct() {
		String bp = device.getProperty("ro.build.product");
		if (bp == null || bp.isEmpty()) {
			bp = device.getProperty("ro.product.name", getDevice());
		}
		return bp;
	}

	@Override
	public String getProductBoard() {
		return device.getProperty("ro.product.board", getDevice());
	}

	@Override
	public String getModel() {
		return device.getProperty("ro.product.model", "Pixel 7a");
	}

	@Override
	public String getBuildId() {
		return device.getProperty("ro.build.id", "BP1A.250405.007.B1");
	}

	@Override
	public String getBuildType() {
		return device.getProperty("ro.build.type", "user");
	}

	@Override
	public String getManufacturer() {
		return device.getProperty("ro.product.manufacturer", "Google");
	}

	@Override
	public String getBootloader() {
		return device.getProperty("ro.bootloader", "lynx-15.2-13237001");
	}

	@Override
	public String getFingerprint() {
		return device.getProperty("ro.build.fingerprint",
				"google/lynx/lynx:15/BP1A.250405.007.B1/13240308:user/release-keys");
	}

	@Override
	public String getCarrier() {
		return device.getProperty("ro.carrier", "Google");
	}

	@Override
	public String getRadio() {
		return device.getProperty("gsm.version.baseband", UNKNOWN);
	}

	@Override
	public String getClientIdBase() {
		return device.getProperty("ro.com.google.clientidbase", "android-google");
	}

	@Override
	public String getLocale() {
		String loc = device.getProperty(LOCALE);
		if (loc != null && !loc.isEmpty()) {
			return loc;
		}
		String lang = device.getProperty("ro.product.locale.language", "en");
		String reg = device.getProperty("ro.product.locale.region", "US");
		return lang + "_" + reg;
	}

	@Override
	public String getTimeZone() {
		return device.getProperty(TIMEZONE, super.getTimeZone());
	}

	@Override
	public String getCellOperator() {
		return device.getProperty(CELLOPERATOR, "310260");
	}

	@Override
	public String getSimOperator() {
		return device.getProperty(SIMOPERATOR, "310260");
	}

	@Override
	public int getScreenDensity() {
		return getInt("ro.sf.lcd_density", 420);
	}

	@Override
	public int getScreenWidth() {
		return getInt(SCREENWIDTH, 1080);
	}

	@Override
	public int getScreenHeight() {
		return getInt(SCREENHEIGHT, 2400);
	}

	@Override
	public int getGlEsVersion() {
		return getInt("ro.opengles.version", 196610);
	}

	@Override
	public List<String> getNativePlatforms() {
		String nat = device.getProperty(PLATFORMS);
		if (nat == null || nat.trim().isEmpty()) {
			nat = device.getProperty("ro.product.cpu.abilist",
					"arm64-v8a,armeabi-v7a,armeabi");
		}
		return splitList(nat);
	}

	@Override
	public List<String> getSharedLibraries() {
		return splitList(device.getProperty(LIBRARIES, ""));
	}

	@Override
	public List<String> getSystemFeatures() {
		return splitList(device.getProperty(FEATURES, ""));
	}

	@Override
	public List<String> getSupportedLocales() {
		String locs = device.getProperty(LOCALES);
		if (locs != null && !locs.trim().isEmpty()) {
			return splitList(locs);
		}
		return super.getSupportedLocales();
	}

}
