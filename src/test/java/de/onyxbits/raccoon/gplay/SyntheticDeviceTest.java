package de.onyxbits.raccoon.gplay;

import static org.junit.Assert.*;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;

import org.junit.Test;

import com.akdeniz.googleplaycrawler.GooglePlay.AndroidCheckinRequest;
import com.akdeniz.googleplaycrawler.GooglePlay.DeviceConfigurationProto;

public class SyntheticDeviceTest {

	@Test
	public void testDefaultDummyDroidPreset() {
		SyntheticDevice dev = SyntheticDevice.createDefault();
		assertEquals("Google", dev.getManufacturer());
		assertEquals("Pixel 7a", dev.getModel());
		assertEquals("lynx", dev.getDevice());
		assertEquals(35, dev.getSdkVersion());
		assertEquals("15", dev.getReleaseVersion());
		assertEquals(1080, dev.getScreenWidth());
		assertEquals(2400, dev.getScreenHeight());
		assertEquals(420, dev.getScreenDensity());
		assertEquals(196610, dev.getGlEsVersion());
		assertTrue(dev.getSharedLibraries().contains("android.test.runner"));
		assertTrue(dev.getSystemFeatures().contains("android.hardware.wifi"));
		assertTrue(dev.getNativePlatforms().contains("arm64-v8a"));

		DeviceConfigurationProto cfg = dev.toDeviceConfigurationProto();
		assertEquals(1080, cfg.getScreenWidth());
		assertEquals(2400, cfg.getScreenHeight());
		assertEquals(420, cfg.getScreenDensity());
		assertTrue(cfg.getSystemSharedLibraryCount() > 20);
		assertTrue(cfg.getSystemAvailableFeatureCount() > 50);
		assertTrue(cfg.getNativePlatformList().contains("arm64-v8a"));

		AndroidCheckinRequest req = dev.toAndroidCheckinRequest();
		assertEquals("lynx", req.getCheckin().getBuild().getDevice());
		assertEquals(35, req.getCheckin().getBuild().getSdkVersion());
		assertEquals("Pixel 7a", req.getCheckin().getBuild().getModel());
	}

	@Test
	public void testRoundTripConvert() throws Exception {
		SyntheticDevice original = SyntheticDevice.createDefault();
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		SyntheticDevice.convert(original, pw);

		Properties loaded = new Properties();
		loaded.load(new StringReader(sw.toString()));
		SyntheticDevice restored = new SyntheticDevice(loaded);

		assertEquals(original.getManufacturer(), restored.getManufacturer());
		assertEquals(original.getModel(), restored.getModel());
		assertEquals(original.getSdkVersion(), restored.getSdkVersion());
		assertEquals(original.getScreenWidth(), restored.getScreenWidth());
		assertEquals(original.getScreenHeight(), restored.getScreenHeight());
		assertEquals(original.getSharedLibraries(), restored.getSharedLibraries());
		assertEquals(original.getSystemFeatures(), restored.getSystemFeatures());
		assertEquals(original.toFinskyUserAgent(), restored.toFinskyUserAgent());
	}
}
