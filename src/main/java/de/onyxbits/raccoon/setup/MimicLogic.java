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
package de.onyxbits.raccoon.setup;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;

import de.onyxbits.raccoon.gplay.PlayProfile;
import de.onyxbits.raccoon.gplay.RealDevice;
import de.onyxbits.raccoon.gui.HyperTextPane;
import de.onyxbits.raccoon.ptools.BridgeManager;
import de.onyxbits.raccoon.ptools.Device;
import de.onyxbits.raccoon.repo.Layout;

/**
 * For entering the parameters of an existing device.
 * 
 * @author patrick
 * 
 */
public class MimicLogic extends WizardBuilder implements CaretListener,
		ActionListener {

	private JTextField gsfId;
	private JTextField userAgent;
	private JButton detectPhone;

	@Override
	protected JPanel assemble() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		gsfId = new JTextField(20);
		gsfId.setMargin(new Insets(2, 2, 2, 2));
		userAgent = new JTextField(20);
		userAgent.setMargin(new Insets(2, 2, 2, 2));
		detectPhone = new JButton("Read from Connected Phone (ADB)");
		HyperTextPane about = new HyperTextPane(
				Messages.getString("ExistingLogic.about")).withWidth(500)
				.withTransparency();
		gsfId.addActionListener(this);
		userAgent.addActionListener(this);
		detectPhone.addActionListener(this);
		gsfId.addCaretListener(this);
		userAgent.addCaretListener(this);

		GridBagConstraints gbc = new GridBagConstraints();
		JPanel container = new JPanel();
		container.setLayout(new GridBagLayout());
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets.left = 5;
		gbc.insets.bottom = 3;
		container.add(new JLabel(Messages.getString("ExistingLogic.gsfid")), gbc);

		gbc.gridx = 1;
		gbc.gridy = 0;
		container.add(gsfId, gbc);

		gbc.gridx = 0;
		gbc.gridy = 1;
		container.add(new JLabel(Messages.getString("ExistingLogic.useragent")),
				gbc);

		gbc.gridx = 1;
		gbc.gridy = 1;
		container.add(userAgent, gbc);

		gbc.gridx = 1;
		gbc.gridy = 2;
		gbc.insets.top = 6;
		container.add(detectPhone, gbc);

		panel.add(about);
		panel.add(Box.createVerticalStrut(20));
		panel.add(container);
		panel.add(Box.createVerticalStrut(20));

		return panel;
	}

	private void detectFromConnectedPhone(boolean showDialogOnError) {
		try {
			BridgeManager bm = new BridgeManager(globals.get(Layout.class));
			bm.startup();
			List<Device> devices = bm.listDevices();
			bm.shutdown();
			if (devices.isEmpty()) {
				if (showDialogOnError) {
					JOptionPane.showMessageDialog(detectPhone,
							"No Android device detected over ADB.\nMake sure the phone is connected via USB and USB Debugging is enabled.",
							"No Device Found", JOptionPane.WARNING_MESSAGE);
				}
				return;
			}
			Device target = null;
			for (Device d : devices) {
				if (d.isAuthorized()) {
					target = d;
					break;
				}
			}
			if (target == null) {
				Device d = devices.get(0);
				if (showDialogOnError) {
					JOptionPane.showMessageDialog(detectPhone,
							"Phone " + d.getSerial() + " is connected, but its status is '"
									+ d.getState()
									+ "'.\nPlease unlock your phone screen and tap 'Allow' on the 'Allow USB debugging?' prompt, then try again.",
							"Device Unauthorized", JOptionPane.WARNING_MESSAGE);
				}
				return;
			}

			RealDevice rd = new RealDevice(target, "41.2.29-31", 84122930);
			userAgent.setText(rd.toFinskyUserAgent());
			String detectedGsf = target.getGsfId();
			if (detectedGsf != null && !detectedGsf.isEmpty()) {
				gsfId.setText(detectedGsf);
			}
			updateNextButton();
		}
		catch (Exception err) {
			if (showDialogOnError) {
				JOptionPane.showMessageDialog(detectPhone,
						"Failed to query device over ADB: " + err.getMessage(),
						"ADB Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void updateNextButton() {
		next.setEnabled(userAgent.getText().trim().length() > 0);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == detectPhone) {
			detectFromConnectedPhone(true);
		}
		if (e.getSource() == gsfId) {
			userAgent.requestFocusInWindow();
		}
		if (e.getSource() == userAgent) {
			onNext();
		}
	}

	@Override
	protected void onActivate() {
		PlayProfile pp = globals.get(PlayProfile.class);
		gsfId.setText(pp.getGsfId() != null ? pp.getGsfId() : "");
		userAgent.setText(pp.getAgent() != null ? pp.getAgent() : "");
		if (userAgent.getText().trim().isEmpty()) {
			detectFromConnectedPhone(false);
		}
		previous.setEnabled(true);
		updateNextButton();
	}

	@Override
	public void caretUpdate(CaretEvent e) {
		updateNextButton();
	}

	@Override
	protected void onNext() {
		PlayProfile pp = globals.get(PlayProfile.class);
		pp.setAgent(userAgent.getText().trim());
		String gsf = gsfId.getText().trim();
		pp.setGsfId(gsf.isEmpty() ? null : gsf);
		show(UploadLogic.class);
	}

	@Override
	protected void onPrevious() {
		show(DeviceLogic.class);
	}

}
