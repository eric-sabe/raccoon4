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

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import com.akdeniz.googleplaycrawler.GooglePlay.AndroidBuildProto;
import com.akdeniz.googleplaycrawler.GooglePlay.AndroidCheckinProto;
import com.akdeniz.googleplaycrawler.GooglePlay.AndroidCheckinRequest;
import com.akdeniz.googleplaycrawler.GooglePlay.DeviceConfigurationProto;

public abstract class MockDevice {

	/**
	 * The string to use for an unknwon property.
	 */
	public static final String UNKNOWN = "unknown";

	public abstract int getFinskyVersionCode();

	public abstract String getFinskyVersion();

	public abstract int getSdkVersion();

	public abstract String getDevice();

	public abstract String getHardware();

	public abstract String getBuildProduct();

	public abstract String getBuildId();

	public abstract String getBuildType();

	public abstract String getManufacturer();

	public abstract String getBootloader();

	public abstract String getFingerprint();

	public abstract int getScreenDensity();

	public abstract int getScreenWidth();

	public abstract int getScreenHeight();

	public abstract int getGlEsVersion();

	public List<String> getSupportedLocales() {
		return Arrays.asList("af", "af_ZA", "am", "am_ET", "ar", "ar_EG", "bg",
				"bg_BG", "ca", "ca_ES", "cs", "cs_CZ", "da", "da_DK", "de", "de_AT",
				"de_CH", "de_DE", "de_LI", "el", "el_GR", "en", "en_AU", "en_CA",
				"en_GB", "en_NZ", "en_SG", "en_US", "es", "es_ES", "es_US", "fa",
				"fa_IR", "fi", "fi_FI", "fr", "fr_BE", "fr_CA", "fr_CH", "fr_FR", "hi",
				"hi_IN", "hr", "hr_HR", "hu", "hu_HU", "in", "in_ID", "it", "it_CH",
				"it_IT", "iw", "iw_IL", "ja", "ja_JP", "ko", "ko_KR", "lt", "lt_LT",
				"lv", "lv_LV", "ms", "ms_MY", "nb", "nb_NO", "nl", "nl_BE", "nl_NL",
				"pl", "pl_PL", "pt", "pt_BR", "pt_PT", "rm", "rm_CH", "ro", "ro_RO",
				"ru", "ru_RU", "sk", "sk_SK", "sl", "sl_SI", "sr", "sr_RS", "sv",
				"sv_SE", "sw", "sw_TZ", "th", "th_TH", "tl", "tl_PH", "tr", "tr_TR",
				"ug", "ug_CN", "uk", "uk_UA", "vi", "vi_VN", "zh_CN", "zh_TW", "zu",
				"zu_ZA");
	}

	public abstract List<String> getNativePlatforms();

	public abstract List<String> getSharedLibraries();

	public abstract List<String> getSystemFeatures();

	public String getModel() {
		return getDevice();
	}

	public String getReleaseVersion() {
		return "15";
	}

	public String getProductBoard() {
		return getDevice();
	}

	public String getCarrier() {
		return "Google";
	}

	public String getRadio() {
		return UNKNOWN;
	}

	public String getClientIdBase() {
		return "android-google";
	}

	public String getLocale() {
		return "en_US";
	}

	public String getTimeZone() {
		String tz = TimeZone.getDefault().getID();
		return (tz != null && !tz.isEmpty()) ? tz : "America/New_York";
	}

	public String getCellOperator() {
		return "310260";
	}

	public String getSimOperator() {
		return "310260";
	}

	/**
	 * Build a complete Google Play {@link DeviceConfigurationProto} from this
	 * mock device (DummyDroid equivalent).
	 */
	public DeviceConfigurationProto toDeviceConfigurationProto() {
		DeviceConfigurationProto.Builder b = DeviceConfigurationProto.newBuilder()
				.setTouchScreen(3)
				.setKeyboard(1)
				.setNavigation(1)
				.setScreenLayout(2)
				.setHasHardKeyboard(false)
				.setHasFiveWayNavigation(false)
				.setScreenDensity(getScreenDensity() > 0 ? getScreenDensity() : 420)
				.setScreenWidth(getScreenWidth() > 0 ? getScreenWidth() : 1080)
				.setScreenHeight(getScreenHeight() > 0 ? getScreenHeight() : 2400)
				.setGlEsVersion(getGlEsVersion() > 0 ? getGlEsVersion() : 196610);

		if (getSharedLibraries() != null && !getSharedLibraries().isEmpty()) {
			b.addAllSystemSharedLibrary(getSharedLibraries());
		}
		if (getSystemFeatures() != null && !getSystemFeatures().isEmpty()) {
			b.addAllSystemAvailableFeature(getSystemFeatures());
		}
		if (getNativePlatforms() != null && !getNativePlatforms().isEmpty()) {
			b.addAllNativePlatform(getNativePlatforms());
		}
		if (getSupportedLocales() != null && !getSupportedLocales().isEmpty()) {
			b.addAllSystemSupportedLocale(getSupportedLocales());
		}
		return b.build();
	}

	/**
	 * Build a complete Google Play {@link AndroidCheckinRequest} from this mock
	 * device (DummyDroid FormData.assemble() equivalent).
	 */
	public AndroidCheckinRequest toAndroidCheckinRequest() {
		AndroidBuildProto.Builder buildProto = AndroidBuildProto.newBuilder()
				.setId(getFingerprint() != null ? getFingerprint() : UNKNOWN)
				.setProduct(getProductBoard() != null ? getProductBoard() : getDevice())
				.setCarrier(getCarrier() != null ? getCarrier() : "Google")
				.setRadio(getRadio() != null ? getRadio() : UNKNOWN)
				.setBootloader(getBootloader() != null ? getBootloader() : UNKNOWN)
				.setClient(
						getClientIdBase() != null ? getClientIdBase() : "android-google")
				.setTimestamp(new Date().getTime() / 1000)
				.setGoogleServices(getSdkVersion() > 0 ? getSdkVersion() : 35)
				.setDevice(getDevice() != null ? getDevice() : UNKNOWN)
				.setSdkVersion(getSdkVersion() > 0 ? getSdkVersion() : 35)
				.setModel(getModel() != null ? getModel() : UNKNOWN)
				.setManufacturer(
						getManufacturer() != null ? getManufacturer() : "Google")
				.setBuildProduct(
						getBuildProduct() != null ? getBuildProduct() : getDevice())
				.setOtaInstalled(false);

		AndroidCheckinProto.Builder checkinProto = AndroidCheckinProto.newBuilder()
				.setBuild(buildProto)
				.setLastCheckinMsec(0)
				.setRoaming("mobile-notroaming")
				.setUserNumber(0);

		if (getCellOperator() != null && !getCellOperator().isEmpty()) {
			checkinProto.setCellOperator(getCellOperator());
		}
		if (getSimOperator() != null && !getSimOperator().isEmpty()) {
			checkinProto.setSimOperator(getSimOperator());
		}

		AndroidCheckinRequest.Builder req = AndroidCheckinRequest.newBuilder()
				.setId(0)
				.setCheckin(checkinProto)
				.setLocale(getLocale() != null ? getLocale() : "en_US")
				.setTimeZone(getTimeZone() != null ? getTimeZone() : "America/New_York")
				.setVersion(3)
				.setDeviceConfiguration(toDeviceConfigurationProto())
				.setFragment(0);

		return req.build();
	}

	/**
	 * Create the user agent string of the play client app
	 * 
	 * @return user agent string
	 */
	public String toFinskyUserAgent() {
		List<String> abis = getNativePlatforms();
		StringBuilder abiStr = new StringBuilder();
		if (abis != null) {
			for (int i = 0; i < abis.size(); i++) {
				if (i > 0) {
					abiStr.append(';');
				}
				abiStr.append(abis.get(i));
			}
		}
		if (abiStr.length() == 0) {
			abiStr.append("arm64-v8a;armeabi-v7a;armeabi");
		}
		String safeModel = getModel() != null ? getModel().replace(' ', '_')
				: getDevice();
		return String.format(
				"Android-Finsky/%s (api=3,versionCode=%d,sdk=%d,device=%s,hardware=%s,product=%s,platformVersionRelease=%s,model=%s,buildId=%s,isWideScreen=0,supportedAbis=%s)",
				getFinskyVersion(), getFinskyVersionCode(), getSdkVersion(),
				getDevice(), getHardware(), getBuildProduct(), getReleaseVersion(),
				safeModel, getBuildId(), abiStr.toString());
	}
}
