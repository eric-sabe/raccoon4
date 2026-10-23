/*
 * Copyright 2016 Patrick Ahlbrecht
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
package de.onyxbits.raccoon.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import com.akdeniz.googleplaycrawler.DownloadData;
import com.akdeniz.googleplaycrawler.GooglePlay.AppDetails;
import com.akdeniz.googleplaycrawler.GooglePlay.BulkDetailsEntry;
import com.akdeniz.googleplaycrawler.GooglePlay.BulkDetailsResponse;
import com.akdeniz.googleplaycrawler.GooglePlay.DetailsResponse;
import com.akdeniz.googleplaycrawler.GooglePlayAPI;
import com.akdeniz.googleplaycrawler.GooglePlayException;

import de.onyxbits.raccoon.db.DatabaseManager;
import de.onyxbits.raccoon.db.VariableDao;
import de.onyxbits.raccoon.db.Variables;
import de.onyxbits.raccoon.gplay.PlayAppOwnerDao;
import de.onyxbits.raccoon.gplay.PlayManager;
import de.onyxbits.raccoon.gplay.PlayProfile;
import de.onyxbits.raccoon.gplay.PlayProfileDao;
import de.onyxbits.raccoon.gplay.RealDevice;
import de.onyxbits.raccoon.gplay.SyntheticDevice;
import de.onyxbits.raccoon.ptools.BridgeManager;
import de.onyxbits.raccoon.ptools.Device;
import de.onyxbits.raccoon.repo.AndroidApp;
import de.onyxbits.raccoon.repo.AndroidAppDao;
import de.onyxbits.raccoon.repo.AppExpansionMainNode;
import de.onyxbits.raccoon.repo.AppExpansionPatchNode;
import de.onyxbits.raccoon.repo.AppIconNode;
import de.onyxbits.raccoon.repo.AppInstallerNode;
import de.onyxbits.raccoon.repo.Layout;
import de.onyxbits.raccoon.setup.LoginLogic;
import de.onyxbits.weave.Globals;

/**
 * Google Play related functions
 * 
 * @author patrick
 * 
 */
class Play implements Variables {

	/**
	 * System property name
	 */
	public static final String PLAYPROFILESYSPROP = "raccoon.playprofile";

	private static GooglePlayAPI createConnection() {
		return PlayManager.createConnection(getProfile());
	}

	private static DatabaseManager getDatabase() {
		Globals globals = GlobalsProvider.getGlobals();
		return globals.get(DatabaseManager.class);
	}

	private static PlayProfile getProfile() {
		DatabaseManager dbm = getDatabase();
		String alias = System.getProperty(PLAYPROFILESYSPROP,
				dbm.get(VariableDao.class).getVar(PLAYPROFILE, null));
		PlayProfile ret = dbm.get(PlayProfileDao.class).get(alias);
		if (ret == null) {
			setupProfile(null, null);
			alias = System.getProperty(PLAYPROFILESYSPROP,
					dbm.get(VariableDao.class).getVar(PLAYPROFILE, null));
			ret = dbm.get(PlayProfileDao.class).get(alias);
		}
		if (ret == null) {
			Router.fail("play.profile");
		}
		return ret;
	}

	private static boolean isAuthFailure(Exception e) {
		if (e instanceof GooglePlayException
				&& ((GooglePlayException) e).getHttpStatus() == 401) {
			return true;
		}
		String msg = e.getMessage();
		return msg != null
				&& (msg.contains("Unauthorized") || msg.contains("DF-DFERH-01"));
	}

	/**
	 * Perform a details query, print the raw results to stdout
	 * 
	 * @param name
	 *          packagename
	 */
	public static void details(String name) {
		GooglePlayAPI api = createConnection();
		try {
			System.out.println(api.details(name));
		}
		catch (Exception e) {
			if (isAuthFailure(e) && refreshFromStoredOrConnectedPhone()) {
				try {
					System.out.println(createConnection().details(name));
					return;
				}
				catch (Exception e2) {
					e = e2;
				}
			}
			failWithAuthHintIfApplicable(e);
		}
	}

	public static void search(String query) {
		GooglePlayAPI api = createConnection();
		try {
			System.out.println(api.search(query));
		}
		catch (Exception e) {
			if (isAuthFailure(e) && refreshFromStoredOrConnectedPhone()) {
				try {
					System.out.println(createConnection().search(query));
					return;
				}
				catch (Exception e2) {
					e = e2;
				}
			}
			failWithAuthHintIfApplicable(e);
		}
	}

	/**
	 * Perform a details query, print to individual files
	 * 
	 * @param input
	 *          file to read packagenames from
	 */
	public static void details(File input) {
		GooglePlayAPI api = createConnection();
		File parent = input.getParentFile();
		Vector<String> tmp = new Vector<String>();
		readPackages(input, tmp);
		for (String name : tmp) {

			File output = new File(parent, name);
			String res = "";
			try {
				res = api.details(name).toString();
			}
			catch (Exception e) {
				// Not found -> empty file
			}
			try {
				FileWriter fw = new FileWriter(output, false);
				fw.write(res);
				fw.close();
			}
			catch (Exception e) {
				Router.fail("play.exception", e.getMessage());
			}
		}
	}

	/**
	 * Perform a bulk details query, write result to individual files
	 * 
	 * @param input
	 *          file containing a list of packagenames.
	 */
	public static void bulkDetails(File input) {
		Vector<String> tmp = new Vector<String>();
		File parent = input.getParentFile();
		readPackages(input, tmp);

		GooglePlayAPI api = createConnection();
		BulkDetailsResponse res = null;
		try {
			res = api.bulkDetails(tmp);
		}
		catch (IOException e) {
			if (isAuthFailure(e) && refreshFromStoredOrConnectedPhone()) {
				try {
					res = createConnection().bulkDetails(tmp);
				}
				catch (IOException e2) {
					failWithAuthHintIfApplicable(e2);
				}
			}
			else {
				failWithAuthHintIfApplicable(e);
			}
		}

		try {
			for (int i = 0; i < tmp.size(); i++) {
				File output = new File(parent, tmp.get(i));
				FileWriter fw = new FileWriter(output, false);
				fw.write(res.getEntry(i).toString());
				fw.close();
			}
		}
		catch (Exception e) {
			Router.fail("play.exception", e.getMessage());
		}
	}

	private static void readPackages(File input, Vector<String> collect) {
		try {
			BufferedReader bi = new BufferedReader(new FileReader(input));
			String line = null;
			while ((line = bi.readLine()) != null) {
				if (line.trim().length() > 0 && !line.startsWith("#")) {
					collect.add(line);
				}
			}
			bi.close();
		}
		catch (Exception e) {
			Router.fail("play.inputfile", input.getAbsolutePath());
		}
	}

	/**
	 * Download an app.
	 * 
	 * @param doc
	 *          packagename
	 * @param vc
	 *          versioncode (maybe -1 to get the latest one).
	 * @param ot
	 *          should always be 1.
	 * @param downloadDir
	 *          alternate download location
	 */
	public static void downloadApp(String doc, int vc, int ot,
			String downloadDir) {
		GooglePlayAPI api = createConnection();
		DatabaseManager dbm = GlobalsProvider.getGlobals()
				.get(DatabaseManager.class);
		boolean paid = false;
		String appTitle = null;
		int vcode = vc;
		int len;
		byte[] buffer = new byte[1024 * 8];

		try {
			DetailsResponse dr;
			try {
				dr = api.details(doc);
			}
			catch (IOException e) {
				if (isAuthFailure(e) && refreshFromStoredOrConnectedPhone()) {
					api = createConnection();
					dr = api.details(doc);
				}
				else {
					throw e;
				}
			}
			appTitle = dr.getDocV2().getTitle();
			paid = dr.getDocV2().getOffer(0).getCheckoutFlowRequired();
			if (vc == -1) {
				vcode = dr.getDocV2().getDetails().getAppDetails().getVersionCode();
			}
		}
		catch (IOException e) {
			failWithAuthHintIfApplicable(e);
		}

		DownloadData data = null;

		try {
			if (paid) {
				// For apps that must be purchased before download
				data = api.delivery(doc, vcode, ot);
			}
			else {
				// for apps that can be downloaded free of charge.
				data = api.purchaseAndDeliver(doc, vcode, ot);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			Router.fail("play.exception", e.getMessage());
		}

		File apkFile = new AppInstallerNode(Layout.DEFAULT, doc, vcode)
				.resolve(downloadDir);
		apkFile.getParentFile().mkdirs();
		File mainFile = new AppExpansionMainNode(Layout.DEFAULT, doc,
				data.getMainFileVersion()).resolve(downloadDir);
		File patchFile = new AppExpansionPatchNode(Layout.DEFAULT, doc,
				data.getPatchFileVersion()).resolve(downloadDir);
		List<File> splitFiles = new ArrayList<File>();

		try {
			InputStream in = data.openApp();
			OutputStream out = new FileOutputStream(apkFile);
			System.out.println(apkFile);
			while ((len = in.read(buffer)) > 0) {
				out.write(buffer, 0, len);
				System.out.print('#');
			}
			System.out.println();
			out.close();
			AndroidApp download = AndroidAppDao.analyze(apkFile);
			if (appTitle != null && !appTitle.isEmpty()
					&& (download.getName().startsWith("@")
							|| download.getName().equals(doc))) {
				download.setName(appTitle);
			}

			if (data.hasMainExpansion()) {
				download.setMainVersion(data.getMainFileVersion());
				in = data.openMainExpansion();
				out = new FileOutputStream(mainFile);
				System.out.println(mainFile);
				while ((len = in.read(buffer)) > 0) {
					out.write(buffer, 0, len);
					System.out.print('#');
				}
				System.out.println();
				out.close();
			}
			if (data.hasPatchExpansion()) {
				download.setPatchVersion(data.getPatchFileVersion());
				in = data.openPatchExpansion();
				out = new FileOutputStream(patchFile);
				System.out.println(patchFile);
				while ((len = in.read(buffer)) > 0) {
					out.write(buffer, 0, len);
					System.out.print('#');
				}
				System.out.println();
				out.close();
			}
			for (int i = 0; i < data.getSplitCount(); i++) {
				File splitFile = new File(apkFile.getParentFile(),
						data.getSplitId(i) + "-" + vcode + ".apk");
				splitFiles.add(splitFile);
				in = data.openSplitDelivery(i);
				out = new FileOutputStream(splitFile);
				System.out.println(splitFile);
				while ((len = in.read(buffer)) > 0) {
					out.write(buffer, 0, len);
					System.out.print('#');
				}
				System.out.println();
				out.close();
			}

			if (downloadDir == "") {
				dbm.get(AndroidAppDao.class).saveOrUpdate(download);
				dbm.get(PlayAppOwnerDao.class).own(download, getProfile());

				AppIconNode ain = new AppIconNode(Layout.DEFAULT, doc, vcode);
				try {
					ain.extractFrom(apkFile);
				}
				catch (IOException e) {
					// This is (probably) ok. Not all APKs contain icons.
				}
			}
		}
		catch (Exception e) {
			apkFile.delete();
			mainFile.delete();
			patchFile.delete();
			for (File splitFile : splitFiles) {
				splitFile.delete();
			}
			Router.fail("play.exception", e.getMessage());
		}
	}

	public static void updateApps() {
		DatabaseManager dbm = getDatabase();
		PlayAppOwnerDao pad = dbm.get(PlayAppOwnerDao.class);
		List<AndroidApp> apps = pad.list(getProfile());
		List<String> pns = new ArrayList<String>(apps.size());
		HashMap<String, AndroidApp> map = new HashMap<String, AndroidApp>();
		for (AndroidApp app : apps) {
			// Note: this check should not exist.
			if (app.getPackageName() != null && !app.getPackageName().equals("")
					&& !pns.contains(app.getPackageName())) {
				pns.add(app.getPackageName());
				map.put(app.getPackageName(), app);
			}
		}
		GooglePlayAPI api = createConnection();
		try {
			BulkDetailsResponse response;
			try {
				response = api.bulkDetails(pns);
			}
			catch (IOException e) {
				if (isAuthFailure(e) && refreshFromStoredOrConnectedPhone()) {
					api = createConnection();
					response = api.bulkDetails(pns);
				}
				else {
					throw e;
				}
			}
			List<BulkDetailsEntry> bde = response.getEntryList();
			for (BulkDetailsEntry entry : bde) {
				AppDetails ad = entry.getDoc().getDetails().getAppDetails();
				String pn = ad.getPackageName();
				int rvc = ad.getVersionCode();
				if (map.containsKey(pn)) {
					int lvc = map.get(pn).getVersionCode();
					if (lvc < rvc) {
						System.out.println("^\t" + pn + "\t" + lvc + "\t->\t" + rvc);
						downloadApp(pn, rvc, 1, "");
					}
					else {
						System.out.println("=\t" + pn + "\t" + lvc + "\t->\t" + rvc);
					}
				}
				else {
					System.out.println("?\t" + pn + "\t0\t->\t" + rvc);
				}
			}
		}
		catch (IOException e) {
			failWithAuthHintIfApplicable(e);
		}
	}

	private static void failWithAuthHintIfApplicable(Exception e) {
		if (isAuthFailure(e)) {
			DatabaseManager dbm = getDatabase();
			String alias = System.getProperty(PLAYPROFILESYSPROP,
					dbm.get(VariableDao.class).getVar(PLAYPROFILE, null));
			PlayProfile pp = dbm.get(PlayProfileDao.class).get(alias);
			if (pp != null && pp.getPassword() != null
					&& pp.getPassword().startsWith("ya29.")) {
				System.err.println(
						"Authentication token expired (HTTP 401 / DF-DFERH-01): The short-lived OAuth2 access token (ya29.*) imported from the phone expires after 1 hour.\n"
								+ "To run Raccoon permanently without the phone connected, set a 16-character Google App Password (from https://myaccount.google.com/apppasswords) or an oauth2_4/... token:\n"
								+ "  java -jar build/libs/Raccoon-desktop-4.25.0.jar --gp-setup \""
								+ pp.getUser() + ",<16_char_app_password>\"");
				System.exit(1);
			}
		}
		Router.fail("play.exception", e.getMessage());
	}

	private static boolean refreshFromStoredOrConnectedPhone() {
		try {
			DatabaseManager dbm = getDatabase();
			String alias = System.getProperty(PLAYPROFILESYSPROP,
					dbm.get(VariableDao.class).getVar(PLAYPROFILE, null));
			PlayProfile pp = dbm.get(PlayProfileDao.class).get(alias);
			if (pp != null && pp.getPassword() != null
					&& !pp.getPassword().trim().isEmpty()
					&& !pp.getPassword().startsWith("ya29.")) {
				GooglePlayAPI api = PlayManager.createConnection(pp);
				api.setClient(LoginLogic.createLoginClient());
				api.login();
				if (api.getToken() != null && !api.getToken().isEmpty()) {
					pp.setToken(api.getToken());
					if (api.getPassword() != null && !api.getPassword().isEmpty()) {
						pp.setPassword(api.getPassword());
					}
					dbm.get(PlayProfileDao.class).update(pp);
					return true;
				}
			}
		}
		catch (Exception e) {
		}
		return refreshFromConnectedPhone();
	}

	private static boolean refreshFromConnectedPhone() {
		try {
			BridgeManager bm = GlobalsProvider.getGlobals().get(BridgeManager.class);
			List<Device> devs = bm.listDevices();
			for (Device d : devs) {
				if (d.isAuthorized()) {
					String fresh = d.getGooglePlayToken(true);
					if (fresh != null && !fresh.isEmpty()) {
						DatabaseManager dbm = getDatabase();
						String alias = System.getProperty(PLAYPROFILESYSPROP,
								dbm.get(VariableDao.class).getVar(PLAYPROFILE, null));
						PlayProfile pp = dbm.get(PlayProfileDao.class).get(alias);
						if (pp != null) {
							pp.setToken(fresh);
							if (pp.getPassword() == null
									|| pp.getPassword().startsWith("ya29.")) {
								pp.setPassword(fresh);
							}
							dbm.get(PlayProfileDao.class).update(pp);
							return true;
						}
					}
				}
			}
		}
		catch (Exception e) {
		}
		return false;
	}

	public static void auth() {
		if (refreshFromConnectedPhone()) {
			return;
		}
		PlayProfile pp = getProfile();
		GooglePlayAPI api = createConnection();
		api.setClient(LoginLogic.createLoginClient());
		try {
			api.login();
			pp.setToken(api.getToken());
			if (api.getPassword() != null && !api.getPassword().isEmpty()) {
				pp.setPassword(api.getPassword());
			}
			getDatabase().get(PlayProfileDao.class).update(pp);
		}
		catch (Exception e) {
			e.printStackTrace();
			Router.fail("play.exception", e.getMessage());
		}
	}

	public static void registerNewGsfId() {
		dummyDroid(null);
	}

	/**
	 * Register a new synthetic DummyDroid device profile and GSF ID with Google
	 * Play without requiring a physical Android phone.
	 * 
	 * @param propertiesPath
	 *          optional path to a DummyDroid .properties or Android build.prop
	 *          file (uses built-in Pixel 7a profile if null/empty)
	 */
	public static void dummyDroid(String propertiesPath) {
		PlayProfile pp = getProfile();
		try {
			SyntheticDevice synth = (propertiesPath != null
					&& !propertiesPath.trim().isEmpty())
							? SyntheticDevice.fromFile(new File(propertiesPath.trim()))
							: SyntheticDevice.createDefault();
			pp.setAgent(synth.toFinskyUserAgent());

			GooglePlayAPI api = PlayManager.createConnection(pp);
			api.setClient(LoginLogic.createLoginClient());

			String oldGsf = pp.getGsfId();
			api.checkin(synth.toAndroidCheckinRequest());
			api.uploadDeviceConfig(synth.toDeviceConfigurationProto(),
					synth.getManufacturer());
			pp.setGsfId(api.getAndroidID());
			if (api.getPassword() != null && !api.getPassword().isEmpty()) {
				pp.setPassword(api.getPassword());
			}
			getDatabase().get(PlayProfileDao.class).update(pp);
			System.out.println("DummyDroid registered synthetic device '"
					+ synth.getManufacturer() + " " + synth.getModel() + " (Android "
					+ synth.getReleaseVersion() + ")' for profile '" + pp.getAlias()
					+ "': " + oldGsf + " -> " + pp.getGsfId());
		}
		catch (Exception e) {
			e.printStackTrace();
			Router.fail("play.exception", e.getMessage());
		}
	}

	/**
	 * Export a DummyDroid .properties profile to disk (from a connected phone if
	 * available, or from the built-in DummyDroid preset otherwise).
	 */
	public static void dummyDroidDump(String outputPath) {
		try {
			de.onyxbits.raccoon.gplay.MockDevice source = null;
			try {
				BridgeManager bm = GlobalsProvider.getGlobals().get(BridgeManager.class);
				for (Device d : bm.listDevices()) {
					if (d.isAuthorized()) {
						source = new RealDevice(d, "53.6.15-34", 85361540);
						System.out.println(
								"Exporting DummyDroid profile from connected phone: "
										+ d.getSerial());
						break;
					}
				}
			}
			catch (Exception e) {
			}
			if (source == null) {
				source = SyntheticDevice.createDefault();
				System.out.println(
						"Exporting built-in DummyDroid profile (Google Pixel 7a)");
			}
			File out = new File(outputPath);
			PrintWriter pw = new PrintWriter(new FileWriter(out));
			try {
				SyntheticDevice.convert(source, pw);
			}
			finally {
				pw.close();
			}
			System.out.println("Saved DummyDroid properties to: "
					+ out.getAbsolutePath());
		}
		catch (Exception e) {
			Router.fail("play.exception", e.getMessage());
		}
	}

	public static void setupProfile(String email, String passwordOrToken) {
		DatabaseManager dbm = getDatabase();
		PlayProfileDao dao = dbm.get(PlayProfileDao.class);
		String detectedAgent = null;
		String detectedGsf = null;

		try {
			BridgeManager bm = GlobalsProvider.getGlobals().get(BridgeManager.class);
			List<Device> devs = bm.listDevices();
			for (Device d : devs) {
				if (d.isAuthorized()) {
					RealDevice rd = new RealDevice(d, "41.2.29-31", 84122930);
					detectedAgent = rd.toFinskyUserAgent();
					String gsf = d.getGsfId();
					if (gsf != null && !gsf.isEmpty()) {
						detectedGsf = gsf;
					}
					if (email == null || email.trim().isEmpty()) {
						email = d.getGoogleAccountEmail();
					}
					if (passwordOrToken == null || passwordOrToken.trim().isEmpty()) {
						passwordOrToken = d.getGooglePlayToken(true);
					}
					System.out.println("Using connected phone profile: " + d.getSerial());
					break;
				}
			}
		}
		catch (Exception e) {
		}

		if (email == null || email.trim().isEmpty() || passwordOrToken == null
				|| passwordOrToken.trim().isEmpty()) {
			Router.fail("play.profile");
			return;
		}

		String alias = email.contains("@") ? email.split("@")[0] : email;
		PlayProfile pp = dao.get(alias);
		boolean isNew = false;
		if (pp == null) {
			pp = new PlayProfile();
			pp.setAlias(alias);
			isNew = true;
		}
		pp.setUser(email);
		pp.setPassword(passwordOrToken);
		if (detectedAgent != null) {
			pp.setAgent(detectedAgent);
		}
		if (detectedGsf != null) {
			pp.setGsfId(detectedGsf);
		}

		SyntheticDevice synth = SyntheticDevice.createDefault();
		if (pp.getAgent() == null) {
			pp.setAgent(synth.toFinskyUserAgent());
		}

		GooglePlayAPI api = PlayManager.createConnection(pp);
		api.setClient(LoginLogic.createLoginClient());
		try {
			api.login();
			pp.setToken(api.getToken());
			if (api.getPassword() != null && !api.getPassword().isEmpty()) {
				pp.setPassword(api.getPassword());
			}
			if (pp.getGsfId() == null || pp.getGsfId().isEmpty()) {
				api.checkin(synth.toAndroidCheckinRequest());
				api.login();
				api.uploadDeviceConfig(synth.toDeviceConfigurationProto(),
						synth.getManufacturer());
				pp.setGsfId(api.getAndroidID());
			}
			if (isNew) {
				dao.add(pp);
			}
			else {
				dao.update(pp);
			}
			dao.set(pp.getAlias());
			System.out.println("Configured profile '" + pp.getAlias()
					+ "' (GSF ID: " + pp.getGsfId() + ")");
		}
		catch (Exception e) {
			Router.fail("play.exception", e.getMessage());
		}
	}

	private static Device requireAuthorizedDevice() {
		BridgeManager bm = GlobalsProvider.getGlobals().get(BridgeManager.class);
		List<Device> devs = bm.listDevices();
		if (devs.isEmpty()) {
			System.err.println(
					"No Android device detected over ADB. Make sure the phone is connected via USB and USB Debugging is enabled.");
			System.exit(1);
		}
		for (Device d : devs) {
			if (d.isAuthorized()) {
				return d;
			}
		}
		Device first = devs.get(0);
		System.err.println("Android device " + first.getSerial()
				+ " is connected, but its ADB status is '" + first.getState()
				+ "'.\nPlease unlock your phone screen and tap 'Allow' on the 'Allow USB debugging?' prompt.");
		System.exit(1);
		return null;
	}

	public static void adbDevices() {
		BridgeManager bm = GlobalsProvider.getGlobals().get(BridgeManager.class);
		List<Device> devs = bm.listDevices();
		if (devs.isEmpty()) {
			System.out.println("No Android devices connected.");
			return;
		}
		for (Device d : devs) {
			if (d.isAuthorized()) {
				String model = d.getProperty("ro.product.model", "Android");
				String rel = d.getProperty("ro.build.version.release", "?");
				System.out.println(d.getSerial() + "\t" + d.getState() + "\t" + model
						+ " (Android " + rel + ")");
			}
			else {
				System.out.println(d.getSerial() + "\t" + d.getState()
						+ "\t(Unlock phone and accept 'Allow USB debugging?' prompt)");
			}
		}
	}

	public static void adbListPackages() {
		Device d = requireAuthorizedDevice();
		try {
			List<String> pkgs = d.getInstalledPackages(true);
			for (String pkg : pkgs) {
				System.out.println(pkg);
			}
		}
		catch (IOException e) {
			Router.fail("play.exception", e.getMessage());
		}
	}

	public static void adbPullPackage(String packageName, String downloadDir) {
		Device d = requireAuthorizedDevice();
		DatabaseManager dbm = GlobalsProvider.getGlobals()
				.get(DatabaseManager.class);
		try {
			List<String> paths = d.getApkPaths(packageName);
			if (paths.isEmpty()) {
				System.err.println("Package not found on device: " + packageName);
				System.exit(1);
			}
			File tmpBase = File.createTempFile("raccoon-adb-", ".apk");
			tmpBase.deleteOnExit();
			byte[] buffer = new byte[1024 * 8];
			int len;

			try (InputStream in = d.createPullStream(paths.get(0));
					OutputStream out = new FileOutputStream(tmpBase)) {
				while ((len = in.read(buffer)) > 0) {
					out.write(buffer, 0, len);
				}
			}

			AndroidApp analyzed = AndroidAppDao.analyze(tmpBase);
			int vcode = analyzed != null ? analyzed.getVersionCode() : 1;
			File apkFile = new AppInstallerNode(Layout.DEFAULT, packageName, vcode)
					.resolve(downloadDir);
			apkFile.getParentFile().mkdirs();
			java.nio.file.Files.move(tmpBase.toPath(), apkFile.toPath(),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			System.out.println(apkFile);

			for (int i = 1; i < paths.size(); i++) {
				String rpath = paths.get(i);
				String name = new File(rpath).getName();
				if (name.startsWith("split_")) {
					name = name.substring("split_".length());
				}
				if (name.endsWith(".apk")) {
					name = name.substring(0, name.length() - 4);
				}
				File splitFile = new File(apkFile.getParentFile(),
						name + "-" + vcode + ".apk");
				try (InputStream in = d.createPullStream(rpath);
						OutputStream out = new FileOutputStream(splitFile)) {
					System.out.println(splitFile);
					while ((len = in.read(buffer)) > 0) {
						out.write(buffer, 0, len);
						System.out.print('#');
					}
					System.out.println();
				}
			}

			if ("".equals(downloadDir) && analyzed != null) {
				dbm.get(AndroidAppDao.class).saveOrUpdate(analyzed);
				AppIconNode ain = new AppIconNode(Layout.DEFAULT, packageName, vcode);
				try {
					ain.extractFrom(apkFile);
				}
				catch (IOException e) {
				}
			}
		}
		catch (Exception e) {
			Router.fail("play.exception", e.getMessage());
		}
	}

}
