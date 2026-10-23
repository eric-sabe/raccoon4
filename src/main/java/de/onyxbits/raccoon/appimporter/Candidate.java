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
package de.onyxbits.raccoon.appimporter;

import java.io.File;
import java.util.List;

import de.onyxbits.raccoon.ptools.Device;
import de.onyxbits.raccoon.repo.AndroidApp;

class Candidate {

	public final File src;
	public final AndroidApp app;
	public final Device device;
	public final String remotePackage;
	public final List<String> remotePaths;

	public Candidate(AndroidApp app, File src) {
		this.app = app;
		this.src = src;
		this.device = null;
		this.remotePackage = null;
		this.remotePaths = null;
	}

	public Candidate(Device device, String remotePackage, List<String> remotePaths) {
		this.app = null;
		this.src = null;
		this.device = device;
		this.remotePackage = remotePackage;
		this.remotePaths = remotePaths;
	}

	@Override
	public String toString() {
		if (remotePackage != null) {
			int splits = remotePaths != null ? Math.max(0, remotePaths.size() - 1) : 0;
			return remotePackage
					+ (splits > 0 ? " (base + " + splits + " splits on phone)" : " (on phone)");
		}
		return app.getName() + " (" + app.getVersion() + "); "
				+ src.getAbsolutePath();
	}
}
