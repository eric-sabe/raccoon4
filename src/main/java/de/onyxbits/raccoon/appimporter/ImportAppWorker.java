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
package de.onyxbits.raccoon.appimporter;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;

import de.onyxbits.raccoon.appmgr.DetailsViewBuilder;
import de.onyxbits.raccoon.db.DatabaseManager;
import de.onyxbits.raccoon.gplay.PlayAppOwnerDao;
import de.onyxbits.raccoon.gplay.PlayProfile;
import de.onyxbits.raccoon.gui.Messages;
import de.onyxbits.raccoon.ptools.Device;
import de.onyxbits.raccoon.repo.AndroidApp;
import de.onyxbits.raccoon.repo.AndroidAppDao;
import de.onyxbits.raccoon.repo.AppGroup;
import de.onyxbits.raccoon.repo.AppInstallerNode;
import de.onyxbits.raccoon.repo.Layout;
import de.onyxbits.raccoon.transfer.TransferPeerBuilder;
import de.onyxbits.raccoon.transfer.TransferWorker;
import de.onyxbits.weave.Globals;
import de.onyxbits.weave.LifecycleManager;
import de.onyxbits.weave.swing.ImageLoaderService;

/**
 * A worker for importing APK files from the local filesystem. Use this one as a
 * reference when implementing a new worker (unfortunately it is not feasible to
 * provide an abstract superclass with common functionality).
 * <p>
 * Implementation note: It is possible (even likely) that a user imports an
 * already existing app for a number of reasons: carelessness, misleading/wrong
 * filenames, replacing an APK with one from another market.
 * <p>
 * We assume the worst case: The user has a hacked version of an app from a
 * shady source in storage and wants to replace it with a clean one.
 * 
 * 
 * @author patrick
 * 
 */
public class ImportAppWorker implements TransferWorker, ActionListener {

	private TransferPeerBuilder control;
	private File source;
	private File dest;
	private File iconDest;
	private Globals globals;
	private long totalBytes;
	private long bytesReceived;
	private AndroidApp transferred;
	private InputStream inputStream;
	private OutputStream outputStream;
	private PlayProfile profile;
	private List<AppGroup> groups;
	private Device remoteDevice;
	private String remotePackage;
	private List<String> remotePaths;
	private List<File> pulledSplits;

	/**
	 * Constructor
	 * 
	 * @param globals
	 *          registry
	 * @param source
	 *          file to copy from.
	 */
	public ImportAppWorker(Globals globals, File source, PlayProfile profile,
			List<AppGroup> groups) {
		this.source = source;
		this.globals = globals;
		control = new TransferPeerBuilder(source.getName());
		control.withChannel(Messages.getString(getClass().getSimpleName()
				+ ".channel"));
		this.profile = profile;
		this.groups = groups;
	}

	/**
	 * Constructor for pulling an app from a connected ADB device.
	 */
	public ImportAppWorker(Globals globals, Device device, String remotePackage,
			List<String> remotePaths, PlayProfile profile, List<AppGroup> groups) {
		this.remoteDevice = device;
		this.remotePackage = remotePackage;
		this.remotePaths = remotePaths;
		this.globals = globals;
		control = new TransferPeerBuilder(remotePackage);
		control.withChannel("ADB Phone \u2192 Local storage");
		this.profile = profile;
		this.groups = groups;
	}

	@Override
	public TransferPeerBuilder getPeer() {
		return control.withViewAction(this);
	}

	@Override
	public InputStream onNextSource() throws Exception {
		if (bytesReceived == 0) {
			return inputStream;
		}
		else {
			return null;
		}
	}

	@Override
	public OutputStream onNextDestination() throws Exception {
		if (bytesReceived == 0) {
			return outputStream;
		}
		else {
			return null;
		}
	}

	@Override
	public float onChunk(int size) {
		bytesReceived += size;
		return (float) bytesReceived / (float) totalBytes;
	}

	@Override
	public void onComplete() throws Exception {
		DatabaseManager dbm = globals.get(DatabaseManager.class);
		dbm.get(AndroidAppDao.class).saveOrUpdate(transferred);
		if (profile != null) {
			dbm.get(PlayAppOwnerDao.class).own(transferred, profile);
		}
		if (pulledSplits != null && dest != null) {
			int vc = transferred.getVersionCode();
			for (File splitTmp : pulledSplits) {
				String name = splitTmp.getName();
				if (name.startsWith("split_")) {
					name = name.substring("split_".length());
				}
				if (name.endsWith(".apk")) {
					name = name.substring(0, name.length() - 4);
				}
				File targetSplit = new File(dest.getParentFile(), name + "-" + vc + ".apk");
				Files.move(splitTmp.toPath(), targetSplit.toPath(),
						StandardCopyOption.REPLACE_EXISTING);
			}
		}
		if (remoteDevice != null && source != null && source.exists()) {
			source.delete();
		}
		closeStreams();
	}

	@Override
	public void onIncomplete(Exception e) {
		dest.delete();
		iconDest.delete();
		closeStreams();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		Window w = globals.get(LifecycleManager.class).getWindow(
				DetailsViewBuilder.ID);
		globals.get(DetailsViewBuilder.class).setApp(transferred);
		w.setVisible(true);
	}

	@Override
	public void onPrepare() throws Exception {

		Layout layout = globals.get(Layout.class);

		if (remoteDevice != null) {
			List<String> paths = (remotePaths != null && !remotePaths.isEmpty())
					? remotePaths
					: remoteDevice.getApkPaths(remotePackage);
			if (paths.isEmpty()) {
				throw new IOException("Could not find APK paths on device for " + remotePackage);
			}
			pulledSplits = new ArrayList<File>();
			for (int i = 0; i < paths.size(); i++) {
				String rpath = paths.get(i);
				String baseName = new File(rpath).getName();
				if (i == 0 || "base.apk".equals(baseName)) {
					File tmpBase = File.createTempFile("raccoon-pull-", ".apk");
					tmpBase.deleteOnExit();
					try (InputStream in = remoteDevice.createPullStream(rpath);
							OutputStream out = new FileOutputStream(tmpBase)) {
						IOUtils.copy(in, out);
					}
					source = tmpBase;
				}
				else {
					File tmpDir = Files.createTempDirectory("raccoon-split-").toFile();
					tmpDir.deleteOnExit();
					File tmpSplit = new File(tmpDir, baseName);
					tmpSplit.deleteOnExit();
					try (InputStream in = remoteDevice.createPullStream(rpath);
							OutputStream out = new FileOutputStream(tmpSplit)) {
						IOUtils.copy(in, out);
					}
					pulledSplits.add(tmpSplit);
				}
			}
		}

		transferred = AndroidAppDao.analyze(source);
		if (transferred == null) {
			throw new IllegalArgumentException("Not an APK!");
		}
		transferred.setGroups(groups);

		AppInstallerNode ain = new AppInstallerNode(layout,
				transferred.getPackageName(), transferred.getVersionCode());
		dest = ain.resolve();
		iconDest = ain.toIcon().resolve();
		dest.getParentFile().mkdirs();
		try {
			ain.toIcon().extractFrom(source);
		}
		catch (Exception e) {
			// Some APKs just don't have an icon.
		}
		inputStream = new FileInputStream(source);
		outputStream = new FileOutputStream(dest);

		globals.get(ImageLoaderService.class).request(control,
				iconDest.toURI().toString());
		totalBytes = Math.max(1, source.length());
	}

	private void closeStreams() {
		try {
			inputStream.close();
			outputStream.close();
		}
		catch (IOException e) {
		}
	}

}
